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
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object IncorrectGroupUpdate extends WorkflowBase {

  case class IncorrectGroupUpdateInputData(group: core.Group) extends InputData {
    override def asJson: JsValue = Json.obj(
      "group" -> JSONConverter.toJsonData(group)
    )
  }

  override def name: String = "core3.test.specs.unit.workflows.definitions.IncorrectGroupUpdate"

  override def readOnly: Boolean = false

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future.successful(NoWorkflowParameters())
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    for {
      groups <- queryHandlers.getAllContainers("Group").map(_.map(_.asInstanceOf[core.Group]))
    } yield {
      IncorrectGroupUpdateInputData(groups.head)
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    data match {
      case actualData: IncorrectGroupUpdateInputData =>
        actualData.group.items :+= core3.database.getNewObjectID
        actualData.group.revision = core3.database.getNewRevisionID
        actualData.group.revisionNumber = core3.database.getNewRevisionSequenceNumber(actualData.group.revisionNumber)

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(update = Vector(actualData.group))))
    }
  }
}
