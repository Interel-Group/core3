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

case class LocalUser(
  userID: String,
  var hashedPassword: String,
  var passwordSalt: String,
  var permissions: Vector[String],
  userType: LocalUser.UserType,
  var metadata: JsValue,
  created: Timestamp,
  var updated: Timestamp,
  var updatedBy: String,
  id: ObjectID,
  var revision: RevisionID,
  var revisionNumber: RevisionSequenceNumber
)
  extends MutableContainer {
  override val objectType: ContainerType = "LocalUser"
}

object LocalUser extends JsonContainerCompanion with SlickContainerCompanion {

  import slick.jdbc.MySQLProfile.api._
  import core3.database.dals.sql.conversions.ForMySQLProfile._
  import shapeless._
  import slickless._

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

    implicit val columnType_userType = MappedColumnType.base[UserType, String](
      { tp => tp.toString }, { str => UserType.fromString(str) }
    )

    implicit val userTypeReads = Reads {
      json =>
        json.validate[String].map(UserType.fromString)
    }

    implicit val userTypeWrites: Writes[UserType] = Writes {
      userType =>
        JsString(userType.toString)
    }
  }

  //
  //SlickContainerCompanion Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[LocalUser](tag, "core_local_users") {
    def userID = column[String]("USER_ID", O.Length(128))

    def hashedPassword = column[String]("HASHED_PASSWORD")

    def passwordSalt = column[String]("PASSWORD_SALT")

    def permissions = column[Vector[String]]("PERMISSIONS")

    def userType = column[UserType]("USER_TYPE")

    def metadata = column[JsValue]("METADATA")

    def created = column[Timestamp]("CREATED", O.SqlType("DATETIME(3)"))

    def updated = column[Timestamp]("UPDATED", O.SqlType("DATETIME(3)"))

    def updatedBy = column[String]("UPDATED_BY")

    def id = column[ObjectID]("ID", O.PrimaryKey)

    def revision = column[RevisionID]("REVISION")

    def revisionNumber = column[RevisionSequenceNumber]("REVISION_NUMBER")

    def * = (userID :: hashedPassword :: passwordSalt :: permissions :: userType :: metadata :: created :: updated :: updatedBy :: id :: revision :: revisionNumber :: HNil).mappedWith(Generic[LocalUser])

    def idx = index("idx_uid", userID, unique = true)
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[ObjectID]) => query.filter(_.id === objectID))
  private val compiledGetByUserID = Compiled((userID: Rep[String]) => query.filter(_.userID === userID))

  override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create
  override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop
  override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result
  override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result
  override def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[LocalUser]
  override def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[LocalUser])
  override def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete

  override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read] = {
    queryName match {
      case "getByUserID" => compiledGetByUserID(queryParams("userID")).result
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.LocalUser::runCustomQuery > Query [$queryName] is not supported.")
    }
  }

  //
  //JsonContainerCompanion Definitions
  //
  private val writes = Writes[LocalUser] {
    obj =>
      Json.obj(
        "userID" -> obj.userID,
        "hashedPassword" -> obj.hashedPassword,
        "passwordSalt" -> obj.passwordSalt,
        "permissions" -> obj.permissions,
        "userType" -> obj.userType,
        "metadata" -> obj.metadata,
        "created" -> obj.created,
        "updated" -> obj.updated,
        "updatedBy" -> obj.updatedBy,
        "id" -> obj.id,
        "revision" -> obj.revision,
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
          (json \ "permissions").as[Vector[String]],
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

  override def toJsonData(container: Container): JsValue = {
    Json.toJson(container.asInstanceOf[LocalUser])(writes)
  }

  override def fromJsonData(data: JsValue): Container = {
    data.as[LocalUser](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  override def getDatabaseName: String = "core-local-users"

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
    permissions: Vector[String],
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
