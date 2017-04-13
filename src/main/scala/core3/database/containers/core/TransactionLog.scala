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
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.MySQLProfile.backend.DatabaseDef
import slick.jdbc.SQLActionBuilder

import scala.concurrent.{ExecutionContext, Future}

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

object TransactionLog
  extends JSONContainerCompanion
    with SlickContainerCompanionImpl[(
    String, String, String, Boolean, String, String, String, Boolean, String, java.sql.Timestamp
    )] {
  private type ContainerTupleDef = (
    String, String, String, Boolean, String, String, String, Boolean, String, java.sql.Timestamp
    )

  //
  //SlickContainerCompanionImpl Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[ContainerTupleDef](tag, "TRANSACTION_LOGS") {
    def id = column[String]("ID", O.PrimaryKey, O.Length(36))

    def workflowName = column[String]("WORKFLOW_NAME")

    def requestID = column[String]("REQUEST_ID", O.Length(36))

    def readOnlyWorkflow = column[Boolean]("READ_ONLY_WORKFLOW")

    def parameters = column[String]("PARAMETERS")

    def data = column[String]("DATA")

    def initiatingUser = column[String]("INITIATING_USER")

    def workflowResult = column[Boolean]("WORKFLOW_RESULT")

    def workflowState = column[String]("WORKFLOW_STATE")

    def timestamp = column[java.sql.Timestamp]("TIMESTAMP", O.SqlType("DATETIME(3)"))

    def * = (id, workflowName, requestID, readOnlyWorkflow, parameters, data, initiatingUser, workflowResult, workflowState, timestamp)
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[String]) => query.filter(_.id === objectID))
  private val compiledGetBetweenTimestamps = Compiled((start: Rep[java.sql.Timestamp], end: Rep[java.sql.Timestamp]) => query.filter(_.timestamp between(start, end)))

  protected def convertToTuple(container: Container): ContainerTupleDef = {
    val c = container.asInstanceOf[TransactionLog]
    val parameters = c.parameters.toString()
    val data = c.data.toString()
    (c.id.toString,
      c.workflowName,
      c.requestID.toString,
      c.readOnlyWorkflow,
      parameters,
      data,
      c.initiatingUser,
      c.workflowResult,
      c.workflowState,
      new java.sql.Timestamp(c.timestamp.getMillis))
  }

  protected def convertFromTuple(tuple: ContainerTupleDef): Container = {
    val id = tuple._1
    val workflowName = tuple._2
    val requestID = tuple._3
    val readOnlyWorkflow = tuple._4
    val parameters = tuple._5
    val data = tuple._6
    val initiatingUser = tuple._7
    val workflowResult = tuple._8
    val workflowState = tuple._9
    val timestamp = tuple._10

    new TransactionLog(
      workflowName,
      getRequestIDFromString(requestID),
      readOnlyWorkflow,
      Json.parse(parameters),
      Json.parse(data),
      initiatingUser,
      workflowResult,
      workflowState,
      new Timestamp(timestamp),
      database.getObjectIDFromString(id)
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
    Future.failed(new IllegalArgumentException("core3.database.containers.core.TransactionLog::runUpdate > Cannot update 'TransactionLog' data."))
  }

  def runDelete(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean] = {
    Future.failed(new IllegalArgumentException("core3.database.containers.core.TransactionLog::runDelete > Cannot delete 'TransactionLog' data."))
  }

  override def runCustomQuery(queryName: String, queryParams: Map[String, String], db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]] = {
    val action = queryName match {
      case "getBetweenTimestamps" =>
        compiledGetBetweenTimestamps((
          new java.sql.Timestamp(queryParams("start").toTimestamp(TimestampFormat.DefaultTimestamp).getMillis),
          new java.sql.Timestamp(queryParams("end").toTimestamp(TimestampFormat.DefaultTimestamp).getMillis)
        )).result

      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.TransactionLog::runCustomQuery > Query [$queryName] is not supported.")
    }

    db.run(action).map {
      result =>
        result.map(convertFromTuple).toVector
    }
  }

  //
  //JSONContainerCompanion Definitions
  //
  private val writes = Writes[TransactionLog] {
    obj =>
      Json.obj(
        "workflowName" -> JsString(obj.workflowName),
        "requestID" -> JsString(obj.requestID.toString),
        "readOnlyWorkflow" -> JsBoolean(obj.readOnlyWorkflow),
        "parameters" -> obj.parameters,
        "data" -> obj.data,
        "initiatingUser" -> JsString(obj.initiatingUser),
        "workflowResult" -> JsBoolean(obj.workflowResult),
        "workflowState" -> JsString(obj.workflowState),
        "timestamp" -> JsString(obj.timestamp.toString),
        "id" -> JsString(obj.id.toString)
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

  def toJsonData(container: Container, format: JsonDataFormat): JsValue = {
    format match {
      case JsonDataFormat.Full => Json.toJson(container.asInstanceOf[TransactionLog])(writes)
      case JsonDataFormat.Partial => Json.toJson(container.asInstanceOf[TransactionLog])(writes)
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.TransactionLog::toJsonData > JSON format [$format] not supported.")
    }
  }

  def fromJsonData(data: JsValue): Container = {
    data.as[TransactionLog](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  def getDatabaseName(dataType: DataType): String = {
    dataType match {
      case DataType.JSON => "core-transaction-logs"
      case DataType.Slick => "TRANSACTION_LOGS"
      case _ => throw new IllegalArgumentException(s"core3.database.containers.core.TransactionLog::getDatabaseName > Data type [$dataType] not supported.")
    }
  }

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
