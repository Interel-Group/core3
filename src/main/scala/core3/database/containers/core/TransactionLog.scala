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
import core3.database.{ContainerType, ObjectID}
import core3.utils.Time._
import core3.utils._
import core3.workflows._
import play.api.libs.json._

case class TransactionLog(
  workflowName: String,
  requestID: RequestID,
  readOnlyWorkflow: Boolean,
  parameters: JsValue,
  data: JsValue,
  initiatingUser: String,
  workflowResult: Boolean,
  workflowState: String,
  timestamp: Timestamp,
  id: ObjectID)
  extends ImmutableContainer {
  override val objectType: ContainerType = "TransactionLog"
}

object TransactionLog extends JsonContainerCompanion with SlickContainerCompanion {

  import slick.jdbc.MySQLProfile.api._
  import core3.database.dals.sql.conversions.ForMySQLProfile._
  import shapeless._
  import slickless._

  //
  //SlickContainerCompanion Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[TransactionLog](tag, "core_transaction_logs") {
    def workflowName = column[String]("WORKFLOW_NAME")

    def requestID = column[RequestID]("REQUEST_ID")

    def readOnlyWorkflow = column[Boolean]("READ_ONLY_WORKFLOW")

    def parameters = column[JsValue]("PARAMETERS")

    def data = column[JsValue]("DATA")

    def initiatingUser = column[String]("INITIATING_USER")

    def workflowResult = column[Boolean]("WORKFLOW_RESULT")

    def workflowState = column[String]("WORKFLOW_STATE")

    def timestamp = column[Timestamp]("TIMESTAMP", O.SqlType("DATETIME(3)"))

    def id = column[ObjectID]("ID", O.PrimaryKey)

    def * = (workflowName :: requestID :: readOnlyWorkflow :: parameters :: data :: initiatingUser :: workflowResult :: workflowState :: timestamp :: id :: HNil).mappedWith(Generic[TransactionLog])
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[ObjectID]) => query.filter(_.id === objectID))
  private val compiledGetBetweenTimestamps = Compiled((start: Rep[Timestamp], end: Rep[Timestamp]) => query.filter(_.timestamp between(start, end)))

  override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create
  override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop
  override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result
  override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result
  override def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[TransactionLog]
  override def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[TransactionLog])
  override def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete

  override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read] = {
    queryName match {
      case "getBetweenTimestamps" =>
        compiledGetBetweenTimestamps((
          queryParams("start").toTimestamp(TimestampFormat.DefaultTimestamp),
          queryParams("end").toTimestamp(TimestampFormat.DefaultTimestamp)
        )).result

      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.TransactionLog::runCustomQuery > Query [$queryName] is not supported.")
    }
  }

  //
  //JsonContainerCompanion Definitions
  //
  private val writes = Writes[TransactionLog] {
    obj =>
      Json.obj(
        "workflowName" -> obj.workflowName,
        "requestID" -> obj.requestID,
        "readOnlyWorkflow" -> obj.readOnlyWorkflow,
        "parameters" -> obj.parameters,
        "data" -> obj.data,
        "initiatingUser" -> obj.initiatingUser,
        "workflowResult" -> obj.workflowResult,
        "workflowState" -> obj.workflowState,
        "timestamp" -> obj.timestamp,
        "id" -> obj.id
      )
  }

  private val reads = Reads[TransactionLog] {
    json =>
      JsSuccess(
        new TransactionLog(
          (json \ "workflowName").as[String],
          (json \ "requestID").as[RequestID],
          (json \ "readOnlyWorkflow").as[Boolean],
          (json \ "parameters").as[JsValue],
          (json \ "data").as[JsValue],
          (json \ "initiatingUser").as[String],
          (json \ "workflowResult").as[Boolean],
          (json \ "workflowState").as[String],
          (json \ "timestamp").as[Timestamp],
          (json \ "id").as[ObjectID]
        )
      )
  }

  override def toJsonData(container: Container): JsValue = {
    Json.toJson(container.asInstanceOf[TransactionLog])(writes)
  }

  override def fromJsonData(data: JsValue): Container = {
    data.as[TransactionLog](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  override def getDatabaseName: String = "core-transaction-logs"

  override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean = {
    queryName match {
      case "getBetweenTimestamps" =>
        val log = container.asInstanceOf[TransactionLog]
        queryParams("start").toTimestamp(TimestampFormat.DefaultTimestamp).isBefore(log.timestamp) && queryParams("end").toTimestamp(TimestampFormat.DefaultTimestamp).isAfter(log.timestamp)

      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.Log::matchCustomQuery > Query [$queryName] is not supported.")
    }
  }

  def apply(
    workflowName: String,
    requestID: RequestID,
    readOnlyWorkflow: Boolean,
    parameters: JsValue,
    data: JsValue,
    initiatingUser: String,
    workflowResult: Boolean,
    workflowState: String
  ) = new TransactionLog(
    workflowName,
    requestID,
    readOnlyWorkflow,
    parameters,
    data,
    initiatingUser,
    workflowResult,
    workflowState,
    Time.getCurrentTimestamp,
    database.getNewObjectID
  )
}
