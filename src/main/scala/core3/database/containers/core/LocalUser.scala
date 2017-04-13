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

sealed trait UserType

object UserType {

  case object Client extends UserType

  case object Service extends UserType

  def fromString(value: String): UserType = {
    value match {
      case "Client" => UserType.Client
      case "Service" => UserType.Service
    }
  }
}

case class LocalUser(
  userID: String,
  var hashedPassword: String,
  var passwordSalt: String,
  var permissions: Seq[String],
  userType: UserType,
  var metadata: JsValue,
  created: Timestamp,
  var updated: Timestamp,
  var updatedBy: String,
  id: ObjectID,
  var revision: RevisionID,
  var revisionNumber: RevisionSequenceNumber)
  extends MutableContainer {
  override val objectType: ContainerType = "LocalUser"
}

object LocalUser
  extends JSONContainerCompanion
    with SlickContainerCompanionImpl[(
    String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp, String, String, Int
    )] {
  private type ContainerTupleDef = (
    String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp, String, String, Int
    )

  //
  //SlickContainerCompanionImpl Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[ContainerTupleDef](tag, "LOCAL_USERS") {
    def id = column[String]("ID", O.PrimaryKey, O.Length(36))

    def userID = column[String]("USER_ID", O.Length(128))

    def hashedPassword = column[String]("HASHED_PASSWORD")

    def passwordSalt = column[String]("PASSWORD_SALT")

    def permissions = column[String]("PERMISSIONS")

    def userType = column[String]("USER_TYPE")

    def metadata = column[String]("METADATA")

    def created = column[java.sql.Timestamp]("CREATED", O.SqlType("DATETIME(3)"))

    def updated = column[java.sql.Timestamp]("UPDATED", O.SqlType("DATETIME(3)"))

    def updatedBy = column[String]("UPDATED_BY", O.Length(36))

    def revision = column[String]("REVISION", O.Length(36))

    def revisionNumber = column[Int]("REVISION_NUMBER")

    def * = (id, userID, hashedPassword, passwordSalt, permissions, userType, metadata, created, updated, updatedBy, revision, revisionNumber)

    def idx = index("idx_uid", userID, unique = true)
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[String]) => query.filter(_.id === objectID))
  private val compiledGetByUserID = Compiled((userID: Rep[String]) => query.filter(_.userID === userID))

  protected def convertToTuple(container: Container): ContainerTupleDef = {
    val c = container.asInstanceOf[LocalUser]
    (c.id.toString,
      c.userID,
      c.hashedPassword,
      c.passwordSalt,
      c.permissions.mkString(","),
      c.userType.toString,
      c.metadata.toString,
      new java.sql.Timestamp(c.created.getMillis),
      new java.sql.Timestamp(c.updated.getMillis),
      c.updatedBy,
      c.revision.toString,
      c.revisionNumber)
  }

  protected def convertFromTuple(tuple: ContainerTupleDef): Container = {
    val id = tuple._1
    val userID = tuple._2
    val hashedPassword = tuple._3
    val passwordSalt = tuple._4
    val permissions = if (tuple._5.isEmpty) {
      Seq.empty
    } else {
      tuple._5.split(",").toSeq
    }
    val userType = UserType.fromString(tuple._6)
    val metadata = Json.parse(tuple._7)
    val created = tuple._8
    val updated = tuple._9
    val updatedBy = tuple._10
    val revision = tuple._11
    val revisionNumber = tuple._12

    new LocalUser(
      userID,
      hashedPassword,
      passwordSalt,
      permissions,
      userType,
      metadata,
      new Timestamp(created),
      new Timestamp(updated),
      updatedBy,
      database.getObjectIDFromString(id),
      database.getRevisionIDFromString(revision),
      revisionNumber
    )
  }

  def runCreateSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query.schema.create)
    } yield {
      true
    }
  }

  def runDropSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query.schema.drop)
    } yield {
      true
    }
  }

  def runGenericQuery(query: SQLActionBuilder, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]] = {
    val action = query.as[ContainerTupleDef]
    db.run(action).map {
      result =>
        result.map(convertFromTuple)
    }
  }

  def runGet(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Container] = {
    val action = compiledGetByID(objectID.toString).result
    db.run(action).map {
      result =>
        convertFromTuple(result.head)
    }
  }

  def runCreate(container: Container, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    for {
      _ <- db.run(query += convertToTuple(container))
    } yield {
      true
    }
  }

  def runUpdate(container: MutableContainer, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    val action = query.filter(_.id === container.id.toString).update(convertToTuple(container))
    db.run(action).map(_ == 1)
  }

  def runDelete(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    val action = query.filter(_.id === objectID.toString).delete
    db.run(action).map(_ == 1)
  }

  override def runCustomQuery(queryName: String, queryParams: Map[String, String], db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]] = {
    val action = queryName match {
      case "getByUserID" => compiledGetByUserID(queryParams("userID")).result
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.LocalUser::runCustomQuery > Query [$queryName] is not supported.")
    }

    db.run(action).map {
      result =>
        result.map(convertFromTuple).toVector
    }
  }

  //
  //JSONContainerCompanion Definitions
  //
  implicit val userTypeReads: Reads[UserType] = Reads {
    json =>
      json.validate[String] match {
        case JsSuccess(value, _) => JsSuccess(UserType.fromString(value))
        case JsError(e) => JsError(e)
      }
  }

  private val writes = Writes[LocalUser] {
    obj =>
      Json.obj(
        "userID" -> obj.userID,
        "hashedPassword" -> obj.hashedPassword,
        "passwordSalt" -> obj.passwordSalt,
        "permissions" -> obj.permissions,
        "userType" -> obj.userType.toString,
        "metadata" -> obj.metadata,
        "created" -> obj.created.toString,
        "updated" -> obj.updated.toString,
        "updatedBy" -> obj.updatedBy,
        "id" -> obj.id.toString,
        "revision" -> obj.revision.toString,
        "revisionNumber" -> obj.revisionNumber
      )
  }

  private val reads = Reads[LocalUser] {
    json =>
      JsSuccess(
        new LocalUser(
          (json \ "userID").as[String],
          (json \ "hashedPassword").as[String],
          (json \ "passwordSalt").as[String],
          (json \ "permissions").as[Seq[String]],
          (json \ "userType").as[UserType],
          (json \ "metadata").as[JsValue],
          (json \ "created").as[Timestamp],
          (json \ "updated").as[Timestamp],
          (json \ "updatedBy").as[String],
          (json \ "id").as[ObjectID],
          (json \ "revision").as[RevisionID],
          (json \ "revisionNumber").as[RevisionSequenceNumber]
        )
      )
  }

  def toJsonData(container: Container, format: JsonDataFormat): JsValue = {
    format match {
      case JsonDataFormat.Full => Json.toJson(container.asInstanceOf[LocalUser])(writes)
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.LocalUser::toJsonData > JSON format [$format] not supported.")
    }
  }

  def fromJsonData(data: JsValue): Container = {
    data.as[LocalUser](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  def getDatabaseName(dataType: DataType): String = {
    dataType match {
      case DataType.JSON => "core-local-users"
      case DataType.Slick => "LOCAL_USERS"
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.LocalUser::getDatabaseName > Data type [$dataType] not supported.")
    }
  }

  override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean = {
    queryName match {
      case "getByUserID" => queryParams("userID") == container.asInstanceOf[LocalUser].userID
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.LocalUser::matchCustomQuery > Query [$queryName] is not supported.")
    }
  }

  def apply(
    userID: String,
    hashedPassword: String,
    passwordSalt: String,
    permissions: Seq[String],
    userType: UserType,
    metadata: JsValue,
    createdBy: String
  ) = new LocalUser(
    userID,
    hashedPassword,
    passwordSalt,
    permissions,
    userType,
    metadata,
    Time.getCurrentTimestamp,
    Time.getCurrentTimestamp,
    createdBy,
    database.getNewObjectID,
    database.getNewRevisionID,
    database.getInitialRevisionSequenceNumber
  )
}
