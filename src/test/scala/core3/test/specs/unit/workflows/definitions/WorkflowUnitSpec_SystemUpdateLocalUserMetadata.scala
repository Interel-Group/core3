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
import core3.database.containers.core.UserType
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

class WorkflowUnitSpec_SystemUpdateLocalUserMetadata extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  private val workflows = Vector(SystemCreateLocalUser, SystemUpdateLocalUserMetadata)
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

  "A SystemUpdateLocalUserMetadata workflow" should "successfully update users" in {
    fixture =>
      val testUserID = "test_uid_1"
      val testHashedPassword = "AbCdE123456!"
      val testPasswordSalt = "01232456_ABC"
      val testPermissions = Seq("A", "B", "C")
      val testUserType = UserType.Service
      val testMetadata = Json.obj("A" -> "BCDE", "B" -> 5)
      val updatedMetadata = Json.obj("A" -> "A", "F" -> false)

      for {
        createResult <- (fixture.engine ? ExecuteWorkflow(
          SystemCreateLocalUser.name,
          rawParams = Json.obj(
            "userID" -> testUserID,
            "hashedPassword" -> testHashedPassword,
            "passwordSalt" -> testPasswordSalt,
            "permissions" -> testPermissions,
            "userType" -> testUserType.toString,
            "metadata" -> testMetadata
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        originalUsers <- fixture.db.queryDatabase("LocalUser").map(_.map(_.asInstanceOf[core.LocalUser]))
        updateResult <- (fixture.engine ? ExecuteWorkflow(
          SystemUpdateLocalUserMetadata.name,
          rawParams = Json.obj(
            "userUUID" -> originalUsers.head.id,
            "revision" -> originalUsers.head.revision,
            "revisionNumber" -> originalUsers.head.revisionNumber,
            "metadata" -> updatedMetadata
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        updatedUsers <- fixture.db.queryDatabase("LocalUser").map(_.map(_.asInstanceOf[core.LocalUser]))
      } yield {
        createResult.wasSuccessful should be(true)
        originalUsers should have size 1
        originalUsers.head.userID should be(testUserID)
        originalUsers.head.hashedPassword should be(testHashedPassword)
        originalUsers.head.passwordSalt should be(testPasswordSalt)
        originalUsers.head.permissions should be(testPermissions)
        originalUsers.head.userType should be(testUserType)
        originalUsers.head.metadata should be(testMetadata)

        updateResult.wasSuccessful should be(true)
        updatedUsers should have size 1
        updatedUsers.head.userID should be(testUserID)
        updatedUsers.head.hashedPassword should be(testHashedPassword)
        updatedUsers.head.passwordSalt should be(testPasswordSalt)
        updatedUsers.head.permissions should be(testPermissions)
        updatedUsers.head.userType should be(testUserType)
        updatedUsers.head.metadata should be(updatedMetadata)
      }
  }
}
