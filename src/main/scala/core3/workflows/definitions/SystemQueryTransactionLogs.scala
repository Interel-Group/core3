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
package core3.workflows.definitions

import core3.database.containers.{JSONConverter, core}
import core3.security.UserTokenBase
import core3.utils.Time._
import core3.utils.{Timestamp, TimestampFormat}
import core3.workflows._
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemQueryTransactionLogs extends WorkflowBase {

  case class SystemQueryTransactionLogsParameters(between: Option[(Timestamp, Timestamp)]) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "between" -> between.map(c => Json.obj("start" -> c._1, "end" -> c._2))
    )
  }

  case class SystemQueryTransactionLogsInputData(logs: Vector[core.TransactionLog]) extends InputData {
    override def asJson: JsValue = Json.obj(
      "logs" -> logs.map(JSONConverter.toJsonData)
    )
  }

  override def name: String = "SystemQueryTransactionLogs"

  override def readOnly: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future.successful(
      SystemQueryTransactionLogsParameters(
        (rawParams \ "between").asOpt[JsObject].map {
          c =>
            (
              c.value("start").as[String].toTimestamp(TimestampFormat.DefaultTimestamp),
              c.value("end").as[String].toTimestamp(TimestampFormat.DefaultTimestamp)
            )
        }
      )
    )
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemQueryTransactionLogsParameters =>
        actualParams.between.fold(queryHandlers.getAllContainers("TransactionLog")) {
          case (start, end) =>
            queryHandlers.getContainers(
              "TransactionLog",
              "getBetweenTimestamps",
              Map(
                "start" -> start.toFormattedString(TimestampFormat.DefaultTimestamp),
                "end" -> end.toFormattedString(TimestampFormat.DefaultTimestamp)
              )
            )
        }.map(c => SystemQueryTransactionLogsInputData(c.map(_.asInstanceOf[core.TransactionLog])))
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    Future.successful(
      (WorkflowResult(wasSuccessful = true, requestID, data = Some(data.asJson)), OutputData())
    )
  }
}
