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

import core3.database.containers.{JSONConverter, JsonDataFormat, core}
import core3.database.{ObjectID, RevisionID, RevisionSequenceNumber}
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemUpdateLocalUserPassword extends WorkflowBase {

  case class SystemUpdateLocalUserPasswordParameters(
    userUUID: ObjectID,
    revision: RevisionID,
    revisionNumber: RevisionSequenceNumber,
    hashedPassword: String,
    passwordSalt: String
  ) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "userUUID" -> userUUID,
      "revision" -> revision,
      "revisionNumber" -> revisionNumber,
      "hashedPassword" -> hashedPassword,
      "passwordSalt" -> passwordSalt
    )
  }

  case class SystemUpdateLocalUserPasswordInputData(user: core.LocalUser) extends InputData {
    override def asJson: JsValue = Json.obj(
      "user" -> JSONConverter.toJsonData(user, JsonDataFormat.Full)
    )
  }

  override def name: String = "core3.workflows.definitions.SystemUpdateLocalUserPassword"

  override def readOnly: Boolean = false

  override def withSensitiveParams: Boolean = true

  override def withSensitiveData: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemUpdateLocalUserPasswordParameters(
        (rawParams \ "userUUID").as[ObjectID],
        (rawParams \ "revision").as[RevisionID],
        (rawParams \ "revisionNumber").as[RevisionSequenceNumber],
        (rawParams \ "hashedPassword").as[String],
        (rawParams \ "passwordSalt").as[String]
      )
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemUpdateLocalUserPasswordParameters =>
        for {
          user <- queryHandlers.getContainerWithRevision(
            "LocalUser",
            actualParams.userUUID,
            actualParams.revision,
            actualParams.revisionNumber
          ).map(_.asInstanceOf[core.LocalUser])
        } yield {
          SystemUpdateLocalUserPasswordInputData(user)
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    (params, data) match {
      case (actualParams: SystemUpdateLocalUserPasswordParameters, actualData: SystemUpdateLocalUserPasswordInputData) =>
        actualData.user.hashedPassword = actualParams.hashedPassword
        actualData.user.passwordSalt = actualParams.passwordSalt

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(update = Vector(actualData.user))))
    }
  }
}
