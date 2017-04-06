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

import core3.database.containers.{JSONConverter, JsonDataFormat, core}
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object UpdateGroups extends WorkflowBase {

  case class UpdateGroupsInputData(groups: Vector[core.Group]) extends InputData {
    override def asJson: JsValue = Json.arr(groups.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full)))
  }

  override def name: String = "core3.test.specs.unit.workflows.definitions.UpdateGroups"

  override def readOnly: Boolean = false

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future.successful(NoWorkflowParameters())
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    for {
      groups <- queryHandlers.getAllContainers("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
    } yield {
      UpdateGroupsInputData(groups)
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    data match {
      case UpdateGroupsInputData(groups) =>

        Future {
          val result = WorkflowResult(wasSuccessful = true, requestID, message = None, data = None)
          val output = OutputData(
            update = groups.map {
              current =>
                current.items :+= core3.database.getNewObjectID
                current
            }
          )

          (result, output)
        }
    }
  }
}
