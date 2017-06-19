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
import core3.database.{ObjectID, RevisionID, RevisionSequenceNumber}
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemDeleteGroup extends WorkflowBase {

  case class SystemDeleteGroupParameters(groupID: ObjectID, revision: RevisionID, revisionNumber: RevisionSequenceNumber) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "groupID" -> groupID,
      "revision" -> revision,
      "revisionNumber" -> revisionNumber
    )
  }

  case class SystemDeleteGroupInputData(group: core.Group) extends InputData {
    override def asJson: JsValue = Json.obj(
      "group" -> group
    )
  }

  override def name: String = "SystemDeleteGroup"

  override def readOnly: Boolean = false

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemDeleteGroupParameters(
        (rawParams \ "groupID").as[ObjectID],
        (rawParams \ "revision").as[RevisionID],
        (rawParams \ "revisionNumber").as[RevisionSequenceNumber])
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemDeleteGroupParameters =>
        for {
          group <- queryHandlers.getContainerWithRevision(
            "Group",
            actualParams.groupID,
            actualParams.revision,
            actualParams.revisionNumber
          ).map(_.asInstanceOf[core.Group])
        } yield {
          SystemDeleteGroupInputData(group)
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    data match {
      case actualData: SystemDeleteGroupInputData =>
        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(delete = Vector(actualData.group))))
    }
  }
}
