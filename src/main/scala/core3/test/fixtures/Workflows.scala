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
package core3.test.fixtures

import akka.actor.ActorRef
import core3.database.dals.DatabaseAbstractionLayer
import core3.security.Auth0UserToken
import core3.test.fixtures
import core3.test.fixtures.TestSystem._
import core3.test.fixtures.workflows._
import core3.workflows.{StoreTransactionLogs, TransactionLogContent, WorkflowBase, WorkflowEngineComponent}
import play.api.libs.json.{JsArray, Json}

object Workflows {
  private val defaultWorkflows: Vector[WorkflowBase] = Vector(AddGroups, IncorrectGroupUpdate, QueryTransactionLogs, UpdateGroups)
  implicit private val definitions = fixtures.Database.defaultDefinitions

  def createWorkflowEngine(
    db: DatabaseAbstractionLayer,
    workflows: Vector[WorkflowBase] = defaultWorkflows,
    readOnlyTransactionLogsEnabled: Boolean = true): ActorRef = {
    system.actorOf(
      WorkflowEngineComponent.props(
        workflows,
        db,
        if (readOnlyTransactionLogsEnabled) {
          StoreTransactionLogs.Always
        } else {
          StoreTransactionLogs.OnWriteOnly
        },
        TransactionLogContent.WithDataAndParams,
        TransactionLogContent.WithDataAndParams
      )(implicitly, definitions),
      name = s"mail_Service_${TestSystem.getNewActorID}"
    )
  }

  def createAuthorizedUser(workflows: Vector[WorkflowBase] = defaultWorkflows): Auth0UserToken = {
    new Auth0UserToken(
      idToken = "none",
      accessToken = "none",
      delegationData = None,
      profile = Json.obj(
        "permissions" -> workflows.map(_.name),
        "user_id" -> "test-user-authorized"
      )
    )
  }

  def createUnauthorizedUser(): Auth0UserToken = {
    new Auth0UserToken(
      idToken = "none",
      accessToken = "none",
      delegationData = None,
      profile = Json.obj(
        "permissions" -> JsArray(Seq()),
        "user_id" -> "test-user-unauthorized"
      )
    )
  }
}
