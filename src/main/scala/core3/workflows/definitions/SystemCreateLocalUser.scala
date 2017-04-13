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
import core3.database.containers.core.UserType
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemCreateLocalUser extends WorkflowBase {

  case class SystemCreateLocalUserParameters(
    userID: String,
    hashedPassword: String,
    passwordSalt: String,
    permissions: Seq[String],
    userType: UserType,
    metadata: JsValue
  ) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "userID" -> userID,
      "hashedPassword" -> hashedPassword,
      "passwordSalt" -> passwordSalt,
      "permissions" -> permissions,
      "userType" -> userType.toString,
      "metadata" -> metadata
    )
  }

  override def name: String = "SystemCreateLocalUser"

  override def readOnly: Boolean = false

  override def withSensitiveParams: Boolean = true

  override def withSensitiveData: Boolean = true

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemCreateLocalUserParameters(
        (rawParams \ "userID").as[String],
        (rawParams \ "hashedPassword").as[String],
        (rawParams \ "passwordSalt").as[String],
        (rawParams \ "permissions").as[Seq[String]],
        (rawParams \ "userType").as[UserType](core.LocalUser.userTypeReads),
        (rawParams \ "metadata").as[JsValue]
      )
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    params match {
      case actualParams: SystemCreateLocalUserParameters =>
        queryHandlers.getContainers("LocalUser", "getByUserID", Map("userID" -> actualParams.userID)).map {
          set =>
            if (set.containers.isEmpty) {
              NoInputData()
            } else {
              throw new RuntimeException(s"core3.workflows.definitions.loadData > Cannot create user; [${set.containers.size}] user(s) with ID [${actualParams.userID}] found.")
            }
        }
    }
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    params match {
      case actualParams: SystemCreateLocalUserParameters =>
        val newUser = core.LocalUser(
          actualParams.userID,
          actualParams.hashedPassword,
          actualParams.passwordSalt,
          actualParams.permissions,
          actualParams.userType,
          actualParams.metadata,
          user.userID
        )

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(add = Vector(newUser))))
    }
  }
}
