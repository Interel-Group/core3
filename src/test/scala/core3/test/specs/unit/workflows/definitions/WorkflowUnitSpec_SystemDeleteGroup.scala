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

class WorkflowUnitSpec_SystemDeleteGroup extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  private val workflows = Vector(SystemCreateGroup, SystemDeleteGroup)
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

  "A SystemDeleteGroup workflow" should "successfully delete groups" in {
    fixture =>
      val testName = "Test Group Name"
      val testShortName = "test_shn"
      val testItemsType = "TransactionLog"
      val testItems = Vector(core3.database.getNewObjectID, core3.database.getNewObjectID)

      for {
        createResult <- (fixture.engine ? ExecuteWorkflow(
          SystemCreateGroup.name,
          rawParams = Json.obj(
            "shortName" -> testShortName,
            "name" -> testName,
            "items" -> testItems,
            "itemsType" -> testItemsType
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        originalGroups <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
        deleteResult <- (fixture.engine ? ExecuteWorkflow(
          SystemDeleteGroup.name,
          rawParams = Json.obj(
            "groupID" -> originalGroups.head.id,
            "revision" -> originalGroups.head.revision,
            "revisionNumber" -> originalGroups.head.revisionNumber
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        updatedGroups <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        createResult.wasSuccessful should be(true)
        originalGroups should have size 1
        originalGroups.head.name should be(testName)
        originalGroups.head.shortName should be(testShortName)
        originalGroups.head.itemsType should be(testItemsType)
        originalGroups.head.items should be(testItems)

        deleteResult.wasSuccessful should be(true)
        updatedGroups should have size 0
      }
  }
}
