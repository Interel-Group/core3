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
import core3.database.containers.core
import core3.database.dals.Core.{BuildAllDatabases, ClearAllDatabases, VerifyAllDatabases}
import core3.database.dals.DatabaseAbstractionLayer
import core3.security.Auth0UserToken
import core3.test.fixtures.{Database, TestSystem}
import core3.test.specs.unit.AsyncUnitSpec
import core3.workflows.WorkflowEngineComponent.ExecuteWorkflow
import core3.workflows.WorkflowResult
import core3.workflows.definitions._
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration._

class WorkflowUnitSpec_SystemCreateGroup extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  private val workflows = Vector(SystemCreateGroup)
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

  "A SystemCreateGroup workflow" should "successfully create groups" in {
    fixture =>
      val testName = "Test Group Name"
      val testShortName = "test_shn"
      val testItemsType = "TransactionLog"
      val testItems = Vector(core3.database.getNewObjectID, core3.database.getNewObjectID)

      for {
        result <- (fixture.engine ? ExecuteWorkflow(
          SystemCreateGroup.name,
          rawParams = Json.obj(
            "shortName" -> testShortName,
            "name" -> testName,
            "items" -> testItems,
            "itemsType" -> testItemsType
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        groups <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        result.wasSuccessful should be(true)
        groups should have size 1
        groups.head.name should be(testName)
        groups.head.shortName should be(testShortName)
        groups.head.itemsType should be(testItemsType)
        groups.head.items should be(testItems)
      }
  }
}
