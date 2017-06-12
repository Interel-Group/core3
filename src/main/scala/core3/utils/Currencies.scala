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

import java.text.DecimalFormat
import java.util.Currency

import play.api.libs.json._

//enables implicit conversions
import scala.language.implicitConversions

sealed trait FiguresFormat

object FiguresFormat {

  case object GroupedDigits extends FiguresFormat

  case object Decimal extends FiguresFormat

  final val groupedDigitsFormatter = new DecimalFormat("###,###")
  final val decimalFormatter = new DecimalFormat("0.00")

  def getFormatter(format: FiguresFormat): DecimalFormat = {
    format match {
      case GroupedDigits => groupedDigitsFormatter
      case Decimal => decimalFormatter
    }
  }
}

/**
  * Object for currency-related functions.
  */
object Currencies {
  implicit val currencyWrites: Writes[Currency] = Writes {
    currency =>
      JsString(currency.toString)
  }

  implicit val currencyReads: Reads[Currency] = Reads {
    json =>
      json.validate[String].map(Currency.getInstance)
  }

  class ExtendedBigDecimal(val self: BigDecimal) {
    def toFormattedString(format: FiguresFormat): String = FiguresFormat.getFormatter(format).format(self)
  }

  implicit def bigDecimalToExtended(d: BigDecimal): ExtendedBigDecimal = new ExtendedBigDecimal(d)
}
