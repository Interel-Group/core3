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

object SystemUpdateLocalUserMetadata extends WorkflowBase {

  case class SystemUpdateLocalUserMetadataParameters(
    userUUID: ObjectID,
    revision: RevisionID,
    revisionNumber: RevisionSequenceNumber,
    metadata: JsValue
  ) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "userUUID" -> userUUID,
      "revision" -> revision,
      "revisionNumber" -> revisionNumber,
      "metadata" -> metadata
    )
  }

  case class SystemUpdateLocalUserMetadataInputData(user: core.LocalUser) extends InputData {
    override def asJson: JsValue = Json.obj(
      "user" -> user
    )
  }

  override def name: String = "SystemUpdateLocalUserMetadata"

  override def readOnly: Boolean = false

  override def withSensitiveParams: Boolean = false

  override def withSensitiveData: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemUpdateLocalUserMetadataParameters(
        (rawParams \ "userUUID").as[ObjectID],
        (rawParams \ "revision").as[RevisionID],
        (rawParams \ "revisionNumber").as[RevisionSequenceNumber],
        (rawParams \ "metadata").as[JsValue]
      )
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemUpdateLocalUserMetadataParameters =>
        for {
          user <- queryHandlers.getContainerWithRevision(
            "LocalUser",
            actualParams.userUUID,
            actualParams.revision,
            actualParams.revisionNumber
          ).map(_.asInstanceOf[core.LocalUser])
        } yield {
          SystemUpdateLocalUserMetadataInputData(user)
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    (params, data) match {
      case (actualParams: SystemUpdateLocalUserMetadataParameters, actualData: SystemUpdateLocalUserMetadataInputData) =>
        actualData.user.metadata = actualParams.metadata

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(update = Vector(actualData.user))))

      case _ =>
        Future.failed(
          new IllegalArgumentException(s"core3.workflows.definitions.SystemUpdateLocalUserMetadata::executeAction > " +
            s"Unexpected params of type [${params.getClass.getName}] or data of type [${data.getClass.getName}] supplied.")
        )
    }
  }
}
