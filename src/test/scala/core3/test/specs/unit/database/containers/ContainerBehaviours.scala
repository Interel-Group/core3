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
package core3.test.specs.unit.database.containers

import java.util.Currency

import akka.util.Timeout
import core3.database._
import core3.database.dals.DatabaseAbstractionLayer
import core3.test.specs.unit.AsyncUnitSpec
import core3.utils.Time._
import core3.utils._
import play.api.libs.json.Json

import scala.concurrent.Future

trait ContainerBehaviours {
  this: AsyncUnitSpec =>

  def standardCoreDAL(db: DatabaseAbstractionLayer)(implicit timeout: Timeout): Unit = {
    it should "successfully query a QueryableContainer by currency" in {
      _ =>
        val EUR_GBP = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp.plusDays(5)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val EUR_USD = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("USD")),
          timestampOpt = Some(getCurrentTimestamp.plusDays(5)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val USD_CHF = QueryableContainer(
          currency = Currency.getInstance("USD"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("CHF")),
          timestampOpt = Some(getCurrentTimestamp.plusDays(5)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        for {
          _ <- db.clearDatabaseStructure("QueryableContainer")
          _ <- db.buildDatabaseStructure("QueryableContainer")
          _ <- db.verifyDatabaseStructure("QueryableContainer")
          _ <- Future.sequence(Seq(db.createObject(EUR_GBP), db.createObject(EUR_USD), db.createObject(USD_CHF)))
          result_EUR <- db.queryDatabase("QueryableContainer", "getByCurrency", Map("currency" -> "EUR"))
          result_GBP <- db.queryDatabase("QueryableContainer", "getByCurrency", Map("currency" -> "GBP"))
          result_USD <- db.queryDatabase("QueryableContainer", "getByCurrency", Map("currency" -> "USD"))
          result_CHF <- db.queryDatabase("QueryableContainer", "getByCurrency", Map("currency" -> "CHF"))
          result_opt_EUR <- db.queryDatabase("QueryableContainer", "getByOptionalCurrency", Map("currency" -> "EUR"))
          result_opt_GBP <- db.queryDatabase("QueryableContainer", "getByOptionalCurrency", Map("currency" -> "GBP"))
          result_opt_USD <- db.queryDatabase("QueryableContainer", "getByOptionalCurrency", Map("currency" -> "USD"))
          result_opt_CHF <- db.queryDatabase("QueryableContainer", "getByOptionalCurrency", Map("currency" -> "CHF"))
          result_all <- db.queryDatabase("QueryableContainer")
        } yield {
          result_EUR should have length 2
          result_EUR.contains(EUR_GBP) should be(true)
          result_EUR.contains(EUR_USD) should be(true)
          result_EUR.contains(USD_CHF) should be(false)

          result_GBP should have length 0
          result_GBP.contains(EUR_GBP) should be(false)
          result_GBP.contains(EUR_USD) should be(false)
          result_GBP.contains(USD_CHF) should be(false)

          result_USD should have length 1
          result_USD.contains(EUR_GBP) should be(false)
          result_USD.contains(EUR_USD) should be(false)
          result_USD.contains(USD_CHF) should be(true)

          result_EUR should have length 2
          result_EUR.contains(EUR_GBP) should be(true)
          result_EUR.contains(EUR_USD) should be(true)
          result_EUR.contains(USD_CHF) should be(false)

          result_CHF should have length 0
          result_CHF.contains(EUR_GBP) should be(false)
          result_CHF.contains(EUR_USD) should be(false)
          result_CHF.contains(USD_CHF) should be(false)

          result_opt_EUR should have length 0
          result_opt_EUR.contains(EUR_GBP) should be(false)
          result_opt_EUR.contains(EUR_USD) should be(false)
          result_opt_EUR.contains(USD_CHF) should be(false)

          result_opt_GBP should have length 1
          result_opt_GBP.contains(EUR_GBP) should be(true)
          result_opt_GBP.contains(EUR_USD) should be(false)
          result_opt_GBP.contains(USD_CHF) should be(false)

          result_opt_USD should have length 1
          result_opt_USD.contains(EUR_GBP) should be(false)
          result_opt_USD.contains(EUR_USD) should be(true)
          result_opt_USD.contains(USD_CHF) should be(false)

          result_opt_EUR should have length 0
          result_opt_EUR.contains(EUR_GBP) should be(false)
          result_opt_EUR.contains(EUR_USD) should be(false)
          result_opt_EUR.contains(USD_CHF) should be(false)

          result_opt_CHF should have length 1
          result_opt_CHF.contains(EUR_GBP) should be(false)
          result_opt_CHF.contains(EUR_USD) should be(false)
          result_opt_CHF.contains(USD_CHF) should be(true)

          result_all should have length 3
          result_all.contains(EUR_GBP) should be(true)
          result_all.contains(EUR_USD) should be(true)
          result_all.contains(USD_CHF) should be(true)
        }
    }

    it should "successfully query a QueryableContainer by timestamp" in {
      _ =>
        val now = getCurrentTimestamp

        val now_nowPlus5Days = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = now,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(now.plusDays(5)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val now_nowPlus1Day = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = now,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(now.plusDays(1)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val nowMinus1Day_nowPlus5Days = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = now.minusDays(1),
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(now.plusDays(5)),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        for {
          _ <- db.clearDatabaseStructure("QueryableContainer")
          _ <- db.buildDatabaseStructure("QueryableContainer")
          _ <- db.verifyDatabaseStructure("QueryableContainer")
          _ <- Future.sequence(Seq(db.createObject(now_nowPlus5Days), db.createObject(now_nowPlus1Day), db.createObject(nowMinus1Day_nowPlus5Days)))
          result_afterNow <- db.queryDatabase("QueryableContainer", "getAfterTimestamp", Map("timestamp" -> now.toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_beforeNow <- db.queryDatabase("QueryableContainer", "getBeforeTimestamp", Map("timestamp" -> now.toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_afterNowMinus1Hour <- db.queryDatabase("QueryableContainer", "getAfterTimestamp", Map("timestamp" -> now.minusHours(1).toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_opt_afterNow <- db.queryDatabase("QueryableContainer", "getAfterOptionalTimestamp", Map("timestamp" -> now.toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_opt_beforeNow <- db.queryDatabase("QueryableContainer", "getBeforeOptionalTimestamp", Map("timestamp" -> now.toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_opt_afterNowPlus1Day <- db.queryDatabase("QueryableContainer", "getAfterOptionalTimestamp", Map("timestamp" -> now.plusDays(1).toFormattedString(TimestampFormat.DefaultTimestamp)))
          result_all <- db.queryDatabase("QueryableContainer")
        } yield {
          result_afterNow should have length 0
          result_afterNow.contains(now_nowPlus5Days) should be(false)
          result_afterNow.contains(now_nowPlus1Day) should be(false)
          result_afterNow.contains(nowMinus1Day_nowPlus5Days) should be(false)

          result_beforeNow should have length 1
          result_beforeNow.contains(now_nowPlus5Days) should be(false)
          result_beforeNow.contains(now_nowPlus1Day) should be(false)
          result_beforeNow.contains(nowMinus1Day_nowPlus5Days) should be(true)

          result_afterNowMinus1Hour should have length 2
          result_afterNowMinus1Hour.contains(now_nowPlus5Days) should be(true)
          result_afterNowMinus1Hour.contains(now_nowPlus1Day) should be(true)
          result_afterNowMinus1Hour.contains(nowMinus1Day_nowPlus5Days) should be(false)

          result_opt_afterNow should have length 3
          result_opt_afterNow.contains(now_nowPlus5Days) should be(true)
          result_opt_afterNow.contains(now_nowPlus1Day) should be(true)
          result_opt_afterNow.contains(nowMinus1Day_nowPlus5Days) should be(true)

          result_opt_beforeNow should have length 0
          result_opt_beforeNow.contains(now_nowPlus5Days) should be(false)
          result_opt_beforeNow.contains(now_nowPlus1Day) should be(false)
          result_opt_beforeNow.contains(nowMinus1Day_nowPlus5Days) should be(false)

          result_opt_afterNowPlus1Day should have length 2
          result_opt_afterNowPlus1Day.contains(now_nowPlus5Days) should be(true)
          result_opt_afterNowPlus1Day.contains(now_nowPlus1Day) should be(false)
          result_opt_afterNowPlus1Day.contains(nowMinus1Day_nowPlus5Days) should be(true)

          result_all should have length 3
          result_all.contains(now_nowPlus5Days) should be(true)
          result_all.contains(now_nowPlus1Day) should be(true)
          result_all.contains(nowMinus1Day_nowPlus5Days) should be(true)
        }
    }

    it should "successfully query a QueryableContainer by date" in {
      _ =>
        val now = getCurrentDate

        val nowPlus1Day = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = now.plusDays(1),
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val nowPlus1Month = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = now.plusMonths(1),
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val nowMinus3Weeks = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = now.minusWeeks(3),
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        for {
          _ <- db.clearDatabaseStructure("QueryableContainer")
          _ <- db.buildDatabaseStructure("QueryableContainer")
          _ <- db.verifyDatabaseStructure("QueryableContainer")
          _ <- Future.sequence(Seq(db.createObject(nowPlus1Day), db.createObject(nowPlus1Month), db.createObject(nowMinus3Weeks)))
          result_tomorrow <- db.queryDatabase("QueryableContainer", "getByDate", Map("date" -> now.plusDays(1).toFormattedString(DateFormat.DefaultDate)))
          result_nextMonth <- db.queryDatabase("QueryableContainer", "getByDate", Map("date" -> now.plusMonths(1).toFormattedString(DateFormat.DefaultDate)))
          result_lastWeek <- db.queryDatabase("QueryableContainer", "getByDate", Map("date" -> now.minusWeeks(1).toFormattedString(DateFormat.DefaultDate)))
          result_all <- db.queryDatabase("QueryableContainer")
        } yield {
          result_tomorrow should have length 1
          result_tomorrow.contains(nowPlus1Day) should be(true)
          result_tomorrow.contains(nowPlus1Month) should be(false)
          result_tomorrow.contains(nowMinus3Weeks) should be(false)

          result_nextMonth should have length 1
          result_nextMonth.contains(nowPlus1Day) should be(false)
          result_nextMonth.contains(nowPlus1Month) should be(true)
          result_nextMonth.contains(nowMinus3Weeks) should be(false)

          result_lastWeek should have length 0
          result_lastWeek.contains(nowPlus1Day) should be(false)
          result_lastWeek.contains(nowPlus1Month) should be(false)
          result_lastWeek.contains(nowMinus3Weeks) should be(false)

          result_all should have length 3
          result_all.contains(nowPlus1Day) should be(true)
          result_all.contains(nowPlus1Month) should be(true)
          result_all.contains(nowMinus3Weeks) should be(true)
        }
    }

    it should "successfully query a QueryableContainer by time" in {
      _ =>
        val now = getCurrentTime

        val nowPlus1Hour = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = now.plusHours(1),
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val nowPlus5Hours = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = now.plusHours(5),
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val nowMinus15Minutes = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = now.minusMinutes(15),
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        for {
          _ <- db.clearDatabaseStructure("QueryableContainer")
          _ <- db.buildDatabaseStructure("QueryableContainer")
          _ <- db.verifyDatabaseStructure("QueryableContainer")
          _ <- Future.sequence(Seq(db.createObject(nowPlus1Hour), db.createObject(nowPlus5Hours), db.createObject(nowMinus15Minutes)))
          result_afterNow <- db.queryDatabase("QueryableContainer", "getAfterTime", Map("time" -> now.toFormattedString(TimeFormat.DefaultTime)))
          result_beforeNow <- db.queryDatabase("QueryableContainer", "getBeforeTime", Map("time" -> now.toFormattedString(TimeFormat.DefaultTime)))
          result_afterNowMinus1Hour <- db.queryDatabase("QueryableContainer", "getAfterTime", Map("time" -> now.minusHours(1).toFormattedString(TimeFormat.DefaultTime)))
          result_all <- db.queryDatabase("QueryableContainer")
        } yield {
          result_afterNow should have length 2
          result_afterNow.contains(nowPlus1Hour) should be(true)
          result_afterNow.contains(nowPlus5Hours) should be(true)
          result_afterNow.contains(nowMinus15Minutes) should be(false)

          result_beforeNow should have length 1
          result_beforeNow.contains(nowPlus1Hour) should be(false)
          result_beforeNow.contains(nowPlus5Hours) should be(false)
          result_beforeNow.contains(nowMinus15Minutes) should be(true)

          result_afterNowMinus1Hour should have length 3
          result_afterNowMinus1Hour.contains(nowPlus1Hour) should be(true)
          result_afterNowMinus1Hour.contains(nowPlus5Hours) should be(true)
          result_afterNowMinus1Hour.contains(nowMinus15Minutes) should be(true)

          result_all should have length 3
          result_all.contains(nowPlus1Hour) should be(true)
          result_all.contains(nowPlus5Hours) should be(true)
          result_all.contains(nowMinus15Minutes) should be(true)
        }
    }

    it should "successfully query a QueryableContainer by enum" in {
      _ =>
        val container1 = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.One,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val container2 = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.Two,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.One),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        val container3 = QueryableContainer(
          currency = Currency.getInstance("EUR"),
          timestamp = getCurrentTimestamp,
          date = getCurrentDate,
          time = getCurrentTime,
          enum = QueryableContainer.TestEnum.Three,
          stringVector = Vector("A", "B", "C"),
          idVector = Vector(getNewObjectID, getNewObjectID),
          jsonOpt = Some(Json.obj("a" -> 1, "b" -> 2)),
          currencyOpt = Some(Currency.getInstance("GBP")),
          timestampOpt = Some(getCurrentTimestamp),
          enumOpt = Some(QueryableContainer.TestEnum.Two),
          stringVectorOpt = Some(Vector("D", "E")),
          idVectorOpt = Some(Vector(getNewObjectID)),
          "test-user"
        )

        for {
          _ <- db.clearDatabaseStructure("QueryableContainer")
          _ <- db.buildDatabaseStructure("QueryableContainer")
          _ <- db.verifyDatabaseStructure("QueryableContainer")
          _ <- Future.sequence(Seq(db.createObject(container1), db.createObject(container2), db.createObject(container3)))
          result_enumOne <- db.queryDatabase("QueryableContainer", "getByAnyEnum", Map("enum" -> QueryableContainer.TestEnum.One.toString))
          result_enumTwo <- db.queryDatabase("QueryableContainer", "getByAnyEnum", Map("enum" -> QueryableContainer.TestEnum.Two.toString))
          result_enumThree <- db.queryDatabase("QueryableContainer", "getByAnyEnum", Map("enum" -> QueryableContainer.TestEnum.Three.toString))
          result_all <- db.queryDatabase("QueryableContainer")
        } yield {
          result_enumOne should have length 2
          result_enumOne.contains(container1) should be(true)
          result_enumOne.contains(container2) should be(true)
          result_enumOne.contains(container3) should be(false)

          result_enumTwo should have length 3
          result_enumTwo.contains(container1) should be(true)
          result_enumTwo.contains(container2) should be(true)
          result_enumTwo.contains(container3) should be(true)

          result_enumThree should have length 1
          result_enumThree.contains(container1) should be(false)
          result_enumThree.contains(container2) should be(false)
          result_enumThree.contains(container3) should be(true)

          result_all should have length 3
          result_all.contains(container1) should be(true)
          result_all.contains(container2) should be(true)
          result_all.contains(container3) should be(true)
        }
    }

    it should "successfully create and query a LargeContainer" in {
      _ =>
        val container1 = LargeContainer(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
          "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
          getNewObjectID
        )

        val container2 = LargeContainer(
          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
          "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
          getNewObjectID
        )

        val container3 = LargeContainer(
          21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
          21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          getNewObjectID
        )

        for {
          _ <- db.clearDatabaseStructure("LargeContainer")
          _ <- db.buildDatabaseStructure("LargeContainer")
          _ <- db.verifyDatabaseStructure("LargeContainer")
          _ <- Future.sequence(Seq(db.createObject(container1), db.createObject(container2), db.createObject(container3)))
          result_all <- db.queryDatabase("LargeContainer")
        } yield {
          result_all should have length 3
          result_all.contains(container1) should be(true)
          result_all.contains(container2) should be(true)
          result_all.contains(container3) should be(true)
        }
    }
  }
}
