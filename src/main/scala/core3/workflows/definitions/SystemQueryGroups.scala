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

import core3.database.containers.core
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemQueryGroups extends WorkflowBase {

  case class SystemQueryGroupsInputData(groups: Vector[core.Group]) extends InputData {
    override def asJson: JsValue = Json.obj(
      "groups" -> groups
    )
  }

  override def name: String = "SystemQueryGroups"

  override def readOnly: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future.successful(NoWorkflowParameters())
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    queryHandlers.getAllContainers("Group").map(c => SystemQueryGroupsInputData(c.map(_.asInstanceOf[core.Group])))
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    Future.successful(
      (WorkflowResult(wasSuccessful = true, requestID, data = Some(data.asJson)), OutputData())
    )
  }
}
