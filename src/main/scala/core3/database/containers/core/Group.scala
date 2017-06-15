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
package core3.database.containers.core

import core3.database
import core3.database.containers._
import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import core3.utils.Time._
import core3.utils._
import play.api.libs.json._

case class Group(
  shortName: String,
  var name: String,
  var items: Vector[ObjectID],
  itemsType: ContainerType,
  created: Timestamp,
  var updated: Timestamp,
  var updatedBy: String,
  id: ObjectID,
  var revision: RevisionID,
  var revisionNumber: RevisionSequenceNumber)
  extends MutableContainer {
  override val objectType: ContainerType = "Group"
}

object Group {

  trait BasicDefinition extends BasicContainerDefinition {
    override def getDatabaseName: String = "core-groups"

    override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean = {
      queryName match {
        case "getByShortName" => queryParams("shortName") == container.asInstanceOf[Group].shortName
        case _ => throw new IllegalArgumentException(s"core3.database.containers.core.Group::matchCustomQuery > Query [$queryName] is not supported.")
      }
    }
  }

  trait JsonDefinition extends JsonContainerDefinition {
    private val writes = Writes[Group] {
      obj =>
        Json.obj(
          "shortName" -> obj.shortName,
          "name" -> obj.name,
          "items" -> obj.items,
          "itemsType" -> obj.itemsType,
          "created" -> obj.created,
          "updated" -> obj.updated,
          "updatedBy" -> obj.updatedBy,
          "id" -> obj.id,
          "revision" -> obj.revision,
          "revisionNumber" -> obj.revisionNumber
        )
    }

    private val reads = Reads[Group] {
      json =>
        JsSuccess(
          new Group(
            (json \ "shortName").as[String],
            (json \ "name").as[String],
            (json \ "items").as[Vector[ObjectID]],
            (json \ "itemsType").as[ContainerType],
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
      Json.toJson(container.asInstanceOf[Group])(writes)
    }

    override def fromJsonData(data: JsValue): Container = {
      data.as[Group](reads)
    }
  }

  trait SlickDefinition { this: SlickContainerDefinition =>

    import profile.api._
    import shapeless._
    import slickless._

    private class TableDef(tag: Tag) extends Table[Group](tag, "core_groups") {

      def shortName = column[String]("SHORT_NAME", O.Length(32))

      def name = column[String]("NAME")

      def items = column[Vector[ObjectID]]("ITEMS")

      def itemsType = column[ContainerType]("ITEMS_TYPE")

      def created = column[Timestamp]("CREATED", O.SqlType("DATETIME(3)"))

      def updated = column[Timestamp]("UPDATED", O.SqlType("DATETIME(3)"))

      def updatedBy = column[String]("UPDATED_BY")

      def id = column[ObjectID]("ID", O.PrimaryKey)

      def revision = column[RevisionID]("REVISION")

      def revisionNumber = column[RevisionSequenceNumber]("REVISION_NUMBER")

      def * = (shortName :: name :: items :: itemsType :: created :: updated :: updatedBy :: id :: revision :: revisionNumber :: HNil).mappedWith(Generic[Group])

      def idx = index("idx_sn", shortName, unique = true)
    }

    private def query = TableQuery[TableDef]

    private def compiledGetByID = Compiled((objectID: Rep[ObjectID]) => query.filter(_.id === objectID))

    private def compiledGetByShortName = Compiled((shortName: Rep[String]) => query.filter(_.shortName === shortName))

    override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create

    override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop

    override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result

    override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result

    override def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[Group]

    override def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[Group])

    override def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete

    override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read] = {
      queryName match {
        case "getByShortName" => compiledGetByShortName(queryParams("shortName")).result
        case _ => throw new IllegalArgumentException(s"core3.database.containers.core.Group::runCustomQuery > Query [$queryName] is not supported.")
      }
    }
  }

  def apply(
    shortName: String,
    name: String,
    items: Vector[ObjectID],
    itemsType: ContainerType,
    createdBy: String
  ) = new Group(
    shortName,
    name,
    items,
    itemsType,
    Time.getCurrentTimestamp,
    Time.getCurrentTimestamp,
    createdBy,
    database.getNewObjectID,
    database.getNewRevisionID,
    database.getInitialRevisionSequenceNumber
  )
}
