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
package core3.test.specs.unit.workflows.definitions

import akka.actor.ActorRef
import akka.pattern.ask
import core3.database.containers.{JSONConverter, core}
import core3.database.dals.Core.{BuildAllDatabases, ClearAllDatabases, VerifyAllDatabases}
import core3.database.dals.DatabaseAbstractionLayer
import core3.security.Auth0UserToken
import core3.test.fixtures.workflows.AddGroups
import core3.test.fixtures.{Database, TestSystem}
import core3.test.specs.unit.AsyncUnitSpec
import core3.workflows.WorkflowEngineComponent.ExecuteWorkflow
import core3.workflows.WorkflowResult
import core3.workflows.definitions._
import play.api.libs.json.{JsArray, Json}

import scala.concurrent.Await
import scala.concurrent.duration._

class WorkflowUnitSpec_SystemQueryGroups extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  private val workflows = Vector(AddGroups, SystemQueryGroups)
  private val authorizedUser = core3.test.fixtures.Workflows.createAuthorizedUser(workflows)
  private val db = Database.createCoreInstance()
  private val engine = core3.test.fixtures.Workflows.createWorkflowEngine(db, workflows, readOnlyTransactionLogsEnabled = false)

  case class FixtureParam(engine: ActorRef, db: DatabaseAbstractionLayer, authorizedUser: Auth0UserToken)

  def withFixture(test: OneArgAsyncTest) = {
    Await.result(
      for {
        _ <- db.getRef ? ClearAllDatabases(ignoreErrors = true)
        _ <- db.getRef ? BuildAllDatabases()
        _ <- db.getRef ? VerifyAllDatabases()
      } yield {
        true
      },
      atMost = 15.seconds
    )

    val fixture = FixtureParam(engine, db, authorizedUser)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  "A SystemQueryGroups workflow" should "successfully query groups" in {
    fixture =>
      for {
        addGroups <- (fixture.engine ? ExecuteWorkflow(AddGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        result <- (fixture.engine ? ExecuteWorkflow(SystemQueryGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        dbGroups <- fixture.db.queryDatabase("Group").map(_.map(_.asInstanceOf[core.Group]))
      } yield {
        addGroups.wasSuccessful should be(true)
        result.wasSuccessful should be(true)
        result.data should not be None

        val workflowGroups = (result.data.get \ "groups").as[JsArray].value.map(c => JSONConverter.fromJsonData("Group", c).asInstanceOf[core.Group]).toVector
        workflowGroups should be(dbGroups)
      }
  }
}
