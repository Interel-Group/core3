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

import core3.database.containers.core
import core3.database.containers.core.UserType
import core3.security.UserTokenBase
import core3.workflows._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object AddLocalUsers extends WorkflowBase {
  override def name: String = "core3.test.specs.unit.workflows.definitions.AddLocalUsers"

  override def readOnly: Boolean = false

  val testUsersCount = 5

  override def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters] = {
    Future.successful(NoWorkflowParameters())
  }

  override def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData] = {
    Future.successful(NoInputData())
  }

  override def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)] = {
    Future {
      val users = for {
        i <- 1 to testUsersCount
      } yield {
        new core.LocalUser(s"testID$i", s"test password #$i", s"test salt #$i", Seq("A", "B"), UserType.Client, Json.obj("C" -> "D"), "test-user")
      }

      (WorkflowResult(wasSuccessful = true, requestID, message = None, data = None), OutputData(add = users.toVector))
    }

  }
}
