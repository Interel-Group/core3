/**
  * Copyright 2017 Interel
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package core3.utils

import javax.inject._

import core3.config.DynamicConfig
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.collection.JavaConversions._

/**
  * Singleton class for handling exchange rate and currency operations.
  * <br><br>
  * Note: Exchange rates are (re)loaded when calling [[getCurrentRates]].
  *
  * @param ws    the web service client to use for retrieving exchange rates
  * @param cache the cache to use for storing the retrieved rates
  */
@Singleton
class ExchangeRates @Inject()(implicit ec: ExecutionContext, ws: WSClient, cache: SyncCacheApi) {
  def availableCurrencies: Vector[String] = DynamicConfig.get.getStringList("currencies.exchange.availableCurrencies").toVector
  def decimalScale: Int = DynamicConfig.get.getInt("currencies.exchange.decimalScale")
  def roundingMode: BigDecimal.RoundingMode.Value = BigDecimal.RoundingMode.withName(DynamicConfig.get.getString("currencies.exchange.roundingMode"))

  private val ratesCacheTimestampKey = "core3.utils.ExchangeRates.timestamp"
  private val ratesCacheDataKey = "core3.utils.ExchangeRates.rates"

  private def providerURI = DynamicConfig.get.getString("currencies.exchange.providerURI")
  private def providerWaitTime = DynamicConfig.get.getInt("currencies.exchange.providerWaitTime").seconds
  private def rateRenewal = DynamicConfig.get.getInt("currencies.exchange.rateRenewal") //in hours

  private val systemLogger = Logger("core3")

  /**
    * Loads the current exchange rates from the configured provider.
    *
    * @return the requested exchange rates
    */
  private def loadExchangeRates: Future[Map[String, BigDecimal]] = {
    val providerResponse = ws.url(providerURI)
      .addHttpHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .get()

    providerResponse.map {
      response =>
        val data = response.json.as[JsObject]
        val baseCurrency = (data \ "base").get.as[String]
        var rates = (data \ "rates").get.as[Map[String, BigDecimal]]
        rates += (baseCurrency -> BigDecimal(1))

        cache.set(ratesCacheTimestampKey, Time.getCurrentTimestamp)
        cache.set(ratesCacheDataKey, rates)

        rates
    }
  }

  /**
    * Retrieves the currently available exchange rates.
    * <br><br>
    * Note: If the previously stored rates have expired or if they have never been retrieved, a request is made to the
    * exchange rate provider and the caller will be forced to wait for that operation to complete.
    *
    * @return the requested exchange rates
    */
  def getCurrentRates: Map[String, BigDecimal] = {
    val ratesDate = cache.get[Timestamp](ratesCacheTimestampKey)
    val rates = cache.get[Map[String, BigDecimal]](ratesCacheDataKey)

    if (ratesDate.isDefined && rates.isDefined) {
      //some rates are available
      if (ratesDate.get.plusHours(rateRenewal).isBefore(Time.getCurrentTimestamp)) {
        //the rates need to be renewed
        try {
          Await.result(loadExchangeRates, providerWaitTime)
        } catch {
          case NonFatal(e) =>
            //could not renew exchange rates; returns the existing ones
            e.printStackTrace()
            systemLogger.error(s"core3.utils.ExchangeRates::getCurrentRates > Exchange rate renewal failed with message [${e.getMessage}].")
            rates.get
        }
      } else {
        //the rates are still valid
        rates.get
      }
    } else {
      //the rates need to be retrieved
      try {
        Await.result(loadExchangeRates, providerWaitTime)
      } catch {
        case NonFatal(e) =>
          //could not retrieve exchange rates
          e.printStackTrace()
          systemLogger.error(s"core3.utils.ExchangeRates::getCurrentRates > Exchange rate retrieval failed with message [${e.getMessage}].")
          throw e
      }
    }
  }

  /**
    * Converts the supplied source value from the specified source currency to the specified target currency.
    *
    * @param sourceValue    the value to convert
    * @param sourceCurrency the value's current currency
    * @param targetCurrency the value's target currency
    * @return the value in the target currency
    */
  def convert(sourceValue: BigDecimal, sourceCurrency: String, targetCurrency: String): BigDecimal =
    convertWith(sourceValue, sourceCurrency, targetCurrency, getCurrentRates, availableCurrencies)

  /**
    * Converts the supplied source value from the specified source currency to the specified target currency.
    *
    * @param sourceValue    the value to convert
    * @param sourceCurrency the value's current currency
    * @param targetCurrency the value's target currency
    * @param rates          the rates to use for the conversion
    * @param currencies     the supported currencies
    * @return the value in the target currency
    */
  def convertWith(sourceValue: BigDecimal, sourceCurrency: String, targetCurrency: String,
    rates: Map[String, BigDecimal], currencies: Vector[String]): BigDecimal = {
    if (currencies.contains(sourceCurrency) && currencies.contains(targetCurrency)
      && rates.contains(sourceCurrency) && rates.contains(targetCurrency)) {
      if (sourceCurrency != targetCurrency) {
        val baseToSourceRate = rates(sourceCurrency)
        val baseToTargetRate = rates(targetCurrency)
        val baseValue = sourceValue / baseToSourceRate
        val targetValue = baseValue * baseToTargetRate
        targetValue.setScale(decimalScale, roundingMode)
      } else {
        sourceValue
      }
    } else {
      val message = s"core3.utils.ExchangeRates::convertWith > Source [$sourceCurrency] or target [$targetCurrency] currency is not available."
      systemLogger.error(message)
      throw new UnsupportedOperationException(message)
    }
  }

  /**
    * Converts a set of currencies to the specified target currency.
    *
    * @param source         the currencies to convert (list of: (source value, source currency))
    * @param targetCurrency the values' target currency
    * @return the converted values
    */
  def convertSet(source: Vector[(BigDecimal, String)], targetCurrency: String): Vector[BigDecimal] =
    convertSetWith(source, targetCurrency, getCurrentRates, availableCurrencies)

  /**
    * Converts a set of currencies to the specified target currency.
    *
    * @param source         the currencies to convert (list of: (source value, source currency))
    * @param targetCurrency the values' target currency
    * @param rates          the rates to use for the conversion
    * @param currencies     the supported currencies
    * @return the converted values
    */
  def convertSetWith(source: Vector[(BigDecimal, String)], targetCurrency: String, rates: Map[String, BigDecimal], currencies: Vector[String]): Vector[BigDecimal] = {
    if (currencies.contains(targetCurrency) && rates.contains(targetCurrency)) {
      source.map {
        case (sourceValue, sourceCurrency) =>
          if (currencies.contains(sourceCurrency) && rates.contains(sourceCurrency)) {
            if (sourceCurrency != targetCurrency) {
              val baseToSourceRate = rates(sourceCurrency)
              val baseToTargetRate = rates(targetCurrency)
              val baseValue = sourceValue / baseToSourceRate
              val targetValue = baseValue * baseToTargetRate
              targetValue.setScale(decimalScale, roundingMode)
            } else {
              sourceValue
            }
          } else {
            val message = s"core3.utils.ExchangeRates::convertSetWith > Source currency [$sourceCurrency] is not available."
            systemLogger.error(message)
            throw new UnsupportedOperationException(message)
          }
      }
    } else {
      val message = s"core3.utils.ExchangeRates::convertSetWith > Target currency [$targetCurrency] is not available."
      systemLogger.error(message)
      throw new UnsupportedOperationException(message)
    }
  }
}
