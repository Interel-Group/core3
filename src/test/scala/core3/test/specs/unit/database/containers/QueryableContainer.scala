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

  sealed trait TestEnum

  object TestEnum {

    case object One extends TestEnum

    case object Two extends TestEnum

    case object Three extends TestEnum

    def fromString(value: String): TestEnum = {
      value match {
        case "One" => TestEnum.One
        case "Two" => TestEnum.Two
        case "Three" => TestEnum.Three
      }
    }
  }

  trait BasicDefinition extends BasicContainerDefinition {
    override def getDatabaseName: String = "core-queryable-container"

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

  trait JsonDefinition extends JsonContainerDefinition {
    implicit val testEnumWrites: Writes[TestEnum] = Writes {
      enum =>
        JsString(enum.toString)
    }

    implicit val testEnumReads = Reads {
      json =>
        json.validate[String].map(TestEnum.fromString)
    }

    private val writes = Writes[QueryableContainer] {
      obj =>
        Json.obj(
          "currency" -> obj.currency,
          "timestamp" -> obj.timestamp,
          "date" -> obj.date,
          "time" -> obj.time,
          "enum" -> obj.enum,
          "stringVector" -> obj.stringVector,
          "idVector" -> obj.idVector,
          "jsonOpt" -> obj.jsonOpt,
          "currencyOpt" -> obj.currencyOpt,
          "timestampOpt" -> obj.timestampOpt,
          "enumOpt" -> obj.enumOpt,
          "stringVectorOpt" -> obj.stringVectorOpt,
          "idVectorOpt" -> obj.idVectorOpt,
          "created" -> obj.created,
          "updated" -> obj.updated,
          "updatedBy" -> obj.updatedBy,
          "id" -> obj.id,
          "revision" -> obj.revision,
          "revisionNumber" -> obj.revisionNumber
        )
    }

    private val reads = Reads[QueryableContainer] {
      json =>
        JsSuccess(
          new QueryableContainer(
            (json \ "currency").as[Currency],
            (json \ "timestamp").as[Timestamp],
            (json \ "date").as[Date],
            (json \ "time").as[Time],
            (json \ "enum").as[TestEnum],
            (json \ "stringVector").as[Vector[String]],
            (json \ "idVector").as[Vector[ObjectID]],
            (json \ "jsonOpt").asOpt[JsValue],
            (json \ "currencyOpt").asOpt[Currency],
            (json \ "timestampOpt").asOpt[Timestamp],
            (json \ "enumOpt").asOpt[TestEnum],
            (json \ "stringVectorOpt").asOpt[Vector[String]],
            (json \ "idVectorOpt").asOpt[Vector[ObjectID]],
            (json \ "created").as[Timestamp],
            (json \ "updated").as[Timestamp],
            (json \ "updatedBy").as[String],
            (json \ "id").as[ObjectID],
            (json \ "revision").as[RevisionID],
            (json \ "revisionNumber").as[RevisionSequenceNumber]
          )
        )
    }

    override def toJsonData(container: Container): JsValue = {
      Json.toJson(container.asInstanceOf[QueryableContainer])(writes)
    }

    override def fromJsonData(data: JsValue): Container = {
      data.as[QueryableContainer](reads)
    }
  }

  trait SlickDefinition { this: SlickContainerDefinition =>

    import profile.api._
    import shapeless._
    import slickless._

    implicit val columnType_testEnum = MappedColumnType.base[TestEnum, String](
      { tp => tp.toString }, { str => TestEnum.fromString(str) }
    )

    private class TableDef(tag: Tag) extends Table[QueryableContainer](tag, "core_queryable_container") {
      def currency = column[Currency]("CURRENCY")

      def timestamp = column[Timestamp]("TIMESTAMP", O.SqlType("DATETIME(3)"))

      def date = column[Date]("DATE")

      def time = column[Time]("TIME", O.SqlType("TIME(3)"))

      def enum = column[TestEnum]("ENUM")

      def stringVector = column[Vector[String]]("STRING_VECTOR")

      def idVector = column[Vector[ObjectID]]("ID_VECTOR")

      def jsonOpt = column[Option[JsValue]]("JSON_OPT")

      def currencyOpt = column[Option[Currency]]("CURRENCY_OPT")

      def timestampOpt = column[Option[Timestamp]]("TIMESTAMP_OPT", O.SqlType("DATETIME(3)"))

      def enumOpt = column[Option[TestEnum]]("ENUM_OPT")

      def stringVectorOpt = column[Option[Vector[String]]]("STRING_VECTOR_OPT")

      def idVectorOpt = column[Option[Vector[ObjectID]]]("ID_VECTOR_OPT")

      def created = column[Timestamp]("CREATED", O.SqlType("DATETIME(3)"))

      def updated = column[Timestamp]("UPDATED", O.SqlType("DATETIME(3)"))

      def updatedBy = column[String]("UPDATED_BY")

      def id = column[ObjectID]("ID", O.PrimaryKey)

      def revision = column[RevisionID]("REVISION")

      def revisionNumber = column[RevisionSequenceNumber]("REVISION_NUMBER")

      def * = (
        currency
          :: timestamp
          :: date
          :: time
          :: enum
          :: stringVector
          :: idVector
          :: jsonOpt
          :: currencyOpt
          :: timestampOpt
          :: enumOpt
          :: stringVectorOpt
          :: idVectorOpt
          :: created
          :: updated
          :: updatedBy
          :: id
          :: revision
          :: revisionNumber
          :: HNil
        ).mappedWith(Generic[QueryableContainer])
    }

    private def query = TableQuery[TableDef]

    private def compiledGetByID = Compiled((objectID: Rep[ObjectID]) => query.filter(_.id === objectID))

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

    override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create

    override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop

    override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result

    override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result

    override def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[QueryableContainer]

    override def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[QueryableContainer])

    override def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete

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
