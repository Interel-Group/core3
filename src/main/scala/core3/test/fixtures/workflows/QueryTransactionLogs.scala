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
package core3.test.fixtures.workflows

import core3.database.containers.{JSONConverter, core}
import core3.security.UserTokenBase
import core3.utils.Time._
import core3.utils._
import core3.workflows._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object QueryTransactionLogs extends WorkflowBase {

  case class QueryTransactionLogsParameters(start: Timestamp, end: Timestamp) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "start" -> start.toFormattedString(TimestampFormat.DefaultTimestamp),
      "end" -> end.toFormattedString(TimestampFormat.DefaultTimestamp)
    )
  }

  case class QueryTransactionLogsInputData(logs: Vector[core.TransactionLog]) extends InputData {
    override def asJson: JsValue = Json.obj(
      "logs" -> logs.map(c => JSONConverter.toJsonData(c))
    )
  }

  override def name: String = "core3.test.specs.unit.workflows.definitions.QueryTransactionLogs"

  override def readOnly: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      val start = (rawParams \ "start").as[String].toTimestamp(TimestampFormat.DefaultTimestamp)
      val end = (rawParams \ "end").as[String].toTimestamp(TimestampFormat.DefaultTimestamp)
      QueryTransactionLogsParameters(start, end)
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: QueryTransactionLogsParameters =>
        queryHandlers.getContainers(
          "TransactionLog",
          "getBetweenTimestamps",
          Map(
            "start" -> actualParams.start.toFormattedString(TimestampFormat.DefaultTimestamp),
            "end" -> actualParams.end.toFormattedString(TimestampFormat.DefaultTimestamp)
          )
        ).map {
          set =>
            QueryTransactionLogsInputData(set.containers.map(_.asInstanceOf[core.TransactionLog]))
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    Future.successful(
      (WorkflowResult(wasSuccessful = true, requestID, data = Some(data.asJson)), OutputData())
    )
  }
}
