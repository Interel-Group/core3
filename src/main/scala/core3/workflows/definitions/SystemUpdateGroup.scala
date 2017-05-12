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
import core3.database.{ObjectID, RevisionID, RevisionSequenceNumber}
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemUpdateGroup extends WorkflowBase {

  case class SystemUpdateGroupParameters(
    groupID: ObjectID,
    revision: RevisionID,
    revisionNumber: RevisionSequenceNumber,
    name: Option[String],
    items: Option[Vector[ObjectID]]
  ) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "groupID" -> groupID,
      "revision" -> revision,
      "revisionNumber" -> revisionNumber,
      "name" -> name,
      "items" -> items
    )
  }

  case class SystemUpdateGroupInputData(group: core.Group) extends InputData {
    override def asJson: JsValue = Json.obj(
      "group" -> JSONConverter.toJsonData(group)
    )
  }

  override def name: String = "SystemUpdateGroup"

  override def readOnly: Boolean = false

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemUpdateGroupParameters(
        (rawParams \ "groupID").as[ObjectID],
        (rawParams \ "revision").as[RevisionID],
        (rawParams \ "revisionNumber").as[RevisionSequenceNumber],
        (rawParams \ "name").asOpt[String],
        (rawParams \ "items").asOpt[JsArray] map (_.value.map(_.as[ObjectID]).to[Vector])
      )
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemUpdateGroupParameters =>
        for {
          group <- queryHandlers.getContainerWithRevision(
            "Group",
            actualParams.groupID,
            actualParams.revision,
            actualParams.revisionNumber
          ).map(_.asInstanceOf[core.Group])
        } yield {
          SystemUpdateGroupInputData(group)
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    (params, data) match {
      case (actualParams: SystemUpdateGroupParameters, actualData: SystemUpdateGroupInputData) =>
        actualParams.name.foreach(name => actualData.group.name = name)
        actualParams.items.foreach(items => actualData.group.items = items)

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(update = Vector(actualData.group))))
    }
  }
}
