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
import core3.database.{ContainerType, ObjectID}
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object SystemCreateGroup extends WorkflowBase {

  case class SystemAddGroupParameters(shortName: String, name: String, items: Vector[ObjectID], itemsType: ContainerType) extends WorkflowParameters {
    override def asJson: JsValue = Json.obj(
      "shortName" -> shortName,
      "name" -> name,
      "items" -> items,
      "itemsType" -> itemsType
    )
  }

  override def name: String = "core3.workflows.definitions.SystemCreateGroup"

  override def readOnly: Boolean = false

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future {
      SystemAddGroupParameters(
        (rawParams \ "shortName").as[String],
        (rawParams \ "name").as[String],
        (rawParams \ "items").as[JsArray].value.map(_.as[ObjectID]).to[Vector],
        (rawParams \ "itemsType").as[String]
      )
    }
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    Future.successful(NoInputData())
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    params match {
      case actualParams: SystemAddGroupParameters =>
        val group = new core.Group(actualParams.shortName, actualParams.name, actualParams.items, actualParams.itemsType, user.userID)

        Future.successful((WorkflowResult(wasSuccessful = true, requestID), OutputData(add = Vector(group))))
    }
  }
}
