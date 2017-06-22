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

import core3.database._
import core3.database.containers._
import core3.utils._
import core3.utils.Time._
import core3.utils.Currencies._
import play.api.libs.json._
import core3.meta.containers._
import core3.meta.enums.DatabaseEnum

@WithBasicContainerDefinition
@WithJsonContainerDefinition
@WithSlickContainerDefinition
case class QueryableContainer(
  currency: Currency,
  timestamp: Timestamp,
  date: Date,
  time: Time,
  enum: QueryableContainer.TestEnum,
  stringVector: Vector[String],
  idVector: Vector[ObjectID],
  jsonOpt: Option[JsValue],
  currencyOpt: Option[Currency],
  timestampOpt: Option[Timestamp],
  enumOpt: Option[QueryableContainer.TestEnum],
  stringVectorOpt: Option[Vector[String]],
  idVectorOpt: Option[Vector[ObjectID]],
  created: Timestamp,
  var updated: Timestamp,
  var updatedBy: String,
  id: ObjectID,
  var revision: RevisionID,
  var revisionNumber: RevisionSequenceNumber
)
  extends MutableContainer {
  override val objectType: ContainerType = "QueryableContainer"
}

object QueryableContainer {

  @DatabaseEnum
  sealed trait TestEnum

  object TestEnum {

    case object One extends TestEnum

    case object Two extends TestEnum

    case object Three extends TestEnum

  }

  trait BasicDefinition extends BasicContainerDefinition {
    override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean = {
      val queryableContainer = container.asInstanceOf[QueryableContainer]
      queryName match {
        case "getByCurrency" => queryableContainer.currency == Currency.getInstance(queryParams("currency"))
        case "getAfterTimestamp" => queryableContainer.timestamp.isAfter(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp))
        case "getBeforeTimestamp" => queryableContainer.timestamp.isBefore(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp))
        case "getByDate" => queryableContainer.date == queryParams("date").toDate(DateFormat.DefaultDate)
        case "getAfterTime" => queryableContainer.time.isAfter(queryParams("time").toTime(TimeFormat.DefaultTime))
        case "getBeforeTime" => queryableContainer.time.isBefore(queryParams("time").toTime(TimeFormat.DefaultTime))
        case "getByAnyEnum" => queryableContainer.enum == TestEnum.fromString(queryParams("enum")) || queryableContainer.enumOpt.contains(TestEnum.fromString(queryParams("enum")))
        case "getByOptionalCurrency" => queryableContainer.currencyOpt.contains(Currency.getInstance(queryParams("currency")))
        case "getAfterOptionalTimestamp" => queryableContainer.timestampOpt.exists(_.isAfter(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp)))
        case "getBeforeOptionalTimestamp" => queryableContainer.timestampOpt.exists(_.isBefore(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp)))
        case _ => throw new IllegalArgumentException(s"core3.test.specs.unit.database.containers.QueryableContainer::matchCustomQuery > Query [$queryName] is not supported.")
      }
    }
  }

  trait SlickDefinition extends SlickContainerDefinition {

    import profile.api._

    private def compiledGetByCurrency = Compiled((currency: Rep[Currency]) => query.filter(_.currency === currency))

    private def compiledGetAfterTimestamp = Compiled((timestamp: Rep[Timestamp]) => query.filter(_.timestamp > timestamp))

    private def compiledGetBeforeTimestamp = Compiled((timestamp: Rep[Timestamp]) => query.filter(_.timestamp < timestamp))

    private def compiledGetByDate = Compiled((date: Rep[Date]) => query.filter(_.date === date))

    private def compiledGetAfterTime = Compiled((time: Rep[Time]) => query.filter(_.time > time))

    private def compiledGetBeforeTime = Compiled((time: Rep[Time]) => query.filter(_.time < time))

    private def compiledGetByAnyEnum = Compiled((enum: Rep[TestEnum]) => query.filter(c => c.enum === enum || c.enumOpt === enum))

    private def compiledGetByOptionalCurrency = Compiled((currency: Rep[Option[Currency]]) => query.filter(_.currencyOpt === currency))

    private def compiledGetAfterOptionalTimestamp = Compiled((timestamp: Rep[Option[Timestamp]]) => query.filter(_.timestampOpt > timestamp))

    private def compiledGetBeforeOptionalTimestamp = Compiled((timestamp: Rep[Option[Timestamp]]) => query.filter(_.timestampOpt < timestamp))

    override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read] = {
      queryName match {
        case "getByCurrency" => compiledGetByCurrency(Currency.getInstance(queryParams("currency"))).result
        case "getAfterTimestamp" => compiledGetAfterTimestamp(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp)).result
        case "getBeforeTimestamp" => compiledGetBeforeTimestamp(queryParams("timestamp").toTimestamp(TimestampFormat.DefaultTimestamp)).result
        case "getByDate" => compiledGetByDate(queryParams("date").toDate(DateFormat.DefaultDate)).result
        case "getAfterTime" => compiledGetAfterTime(queryParams("time").toTime(TimeFormat.DefaultTime)).result
        case "getBeforeTime" => compiledGetBeforeTime(queryParams("time").toTime(TimeFormat.DefaultTime)).result
        case "getByAnyEnum" => compiledGetByAnyEnum(TestEnum.fromString(queryParams("enum"))).result
        case "getByOptionalCurrency" => compiledGetByOptionalCurrency(queryParams.get("currency").map(Currency.getInstance)).result
        case "getAfterOptionalTimestamp" => compiledGetAfterOptionalTimestamp(queryParams.get("timestamp").map(_.toTimestamp(TimestampFormat.DefaultTimestamp))).result
        case "getBeforeOptionalTimestamp" => compiledGetBeforeOptionalTimestamp(queryParams.get("timestamp").map(_.toTimestamp(TimestampFormat.DefaultTimestamp))).result
        case _ => throw new IllegalArgumentException(s"core3.test.specs.unit.database.containers.QueryableContainer::runCustomQuery > Query [$queryName] is not supported.")
      }
    }
  }

  def apply(
    currency: Currency,
    timestamp: Timestamp,
    date: Date,
    time: Time,
    enum: QueryableContainer.TestEnum,
    stringVector: Vector[String],
    idVector: Vector[ObjectID],
    jsonOpt: Option[JsValue],
    currencyOpt: Option[Currency],
    timestampOpt: Option[Timestamp],
    enumOpt: Option[QueryableContainer.TestEnum],
    stringVectorOpt: Option[Vector[String]],
    idVectorOpt: Option[Vector[ObjectID]],
    createdBy: String
  ) = new QueryableContainer(
    currency,
    timestamp,
    date,
    time,
    enum,
    stringVector,
    idVector,
    jsonOpt,
    currencyOpt,
    timestampOpt,
    enumOpt,
    stringVectorOpt,
    idVectorOpt,
    Time.getCurrentTimestamp,
    Time.getCurrentTimestamp,
    createdBy,
    getNewObjectID,
    getNewRevisionID,
    getInitialRevisionSequenceNumber
  )
}
