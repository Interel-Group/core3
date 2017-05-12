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
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.MySQLProfile.backend.DatabaseDef
import slick.jdbc.SQLActionBuilder

import scala.concurrent.{ExecutionContext, Future}

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

object Group
  extends JsonContainerCompanion
    with SlickContainerCompanionImpl[(
    String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp, String, String, Int
    )] {
  private type ContainerTupleDef = (
    String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp, String, String, Int
    )

  //
  //SlickContainerCompanionImpl Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[ContainerTupleDef](tag, "core_groups") {
    def id = column[String]("ID", O.PrimaryKey, O.Length(36))

    def shortName = column[String]("SHORT_NAME", O.Length(32))

    def name = column[String]("NAME")

    def items = column[String]("ITEMS")

    def itemsType = column[String]("ITEMS_TYPE")

    def created = column[java.sql.Timestamp]("CREATED", O.SqlType("DATETIME(3)"))

    def updated = column[java.sql.Timestamp]("UPDATED", O.SqlType("DATETIME(3)"))

    def updatedBy = column[String]("UPDATED_BY")

    def revision = column[String]("REVISION", O.Length(36))

    def revisionNumber = column[Int]("REVISION_NUMBER")

    def * = (id, shortName, name, items, itemsType, created, updated, updatedBy, revision, revisionNumber)

    def idx = index("idx_sn", shortName, unique = true)
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[String]) => query.filter(_.id === objectID))
  private val compiledGetByShortName = Compiled((shortName: Rep[String]) => query.filter(_.shortName === shortName))

  override protected def convertToTuple(container: Container): ContainerTupleDef = {
    val c = container.asInstanceOf[Group]
    (c.id.toString,
      c.shortName,
      c.name,
      c.items.mkString(","),
      c.itemsType.toString,
      new java.sql.Timestamp(c.created.getMillis),
      new java.sql.Timestamp(c.updated.getMillis),
      c.updatedBy,
      c.revision.toString,
      c.revisionNumber)
  }

  override protected def convertFromTuple(tuple: ContainerTupleDef): Container = {
    val id = tuple._1
    val shortName = tuple._2
    val name = tuple._3
    val items = if (tuple._4.isEmpty) {
      Vector.empty
    } else {
      tuple._4.split(",").map { current => database.getObjectIDFromString(current) }.to[Vector]
    }
    val itemsType = tuple._5
    val created = tuple._6
    val updated = tuple._7
    val updatedBy = tuple._8
    val revision = tuple._9
    val revisionNumber = tuple._10

    new Group(
      shortName,
      name,
      items,
      itemsType,
      new Timestamp(created),
      new Timestamp(updated),
      updatedBy,
      database.getObjectIDFromString(id),
      database.getRevisionIDFromString(revision),
      revisionNumber
    )
  }

  override def runCreateSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query.schema.create)
    } yield {
      true
    }
  }

  override def runDropSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query.schema.drop)
    } yield {
      true
    }
  }

  override def runGenericQuery(query: SQLActionBuilder, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]] = {
    val action = query.as[ContainerTupleDef]
    db.run(action).map {
      result =>
        result.map(convertFromTuple)
    }
  }

  override def runGet(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Container] = {
    val action = compiledGetByID(objectID.toString).result
    db.run(action).map {
      result =>
        convertFromTuple(result.head)
    }
  }

  override def runCreate(container: Container, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query += convertToTuple(container))
    } yield {
      true
    }
  }

  override def runUpdate(container: MutableContainer, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    val action = query.filter(_.id === container.id.toString).update(convertToTuple(container))
    db.run(action).map(_ == 1)
  }

  override def runDelete(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    val action = query.filter(_.id === objectID.toString).delete
    db.run(action).map(_ == 1)
  }

  override def runCustomQuery(queryName: String, queryParams: Map[String, String], db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]] = {
    val action = queryName match {
      case "getByShortName" => compiledGetByShortName(queryParams("shortName")).result
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.Group::runCustomQuery > Query [$queryName] is not supported.")
    }

    db.run(action).map {
      result =>
        result.map(convertFromTuple).toVector
    }
  }

  //
  //JsonContainerCompanion Definitions
  //
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

  private val cacheWrites = Writes[Group] {
    obj =>
      Json.obj(
        "shortName" -> obj.shortName,
        "name" -> obj.name,
        "items" -> obj.items,
        "itemsType" -> obj.itemsType,
        "id" -> obj.id
      )
  }

  override def toJsonData(container: Container): JsValue = {
    Json.toJson(container.asInstanceOf[Group])(writes)
  }

  override def fromJsonData(data: JsValue): Container = {
    data.as[Group](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  override def getDatabaseName: String = "core-groups"

  override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean = {
    queryName match {
      case "getByShortName" => queryParams("shortName") == container.asInstanceOf[Group].shortName
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.Group::matchCustomQuery > Query [$queryName] is not supported.")
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
