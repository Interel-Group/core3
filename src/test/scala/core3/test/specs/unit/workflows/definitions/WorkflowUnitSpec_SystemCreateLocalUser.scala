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

class WorkflowUnitSpec_SystemCreateLocalUser extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  private val workflows = Vector(SystemCreateLocalUser)
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

  "A SystemCreateLocalUser workflow" should "successfully create users" in {
    fixture =>
      val testUserID = "test_uid_1"
      val testHashedPassword = "AbCdE123456!"
      val testPasswordSalt = "01232456_ABC"
      val testPermissions = Seq("A", "B", "C")
      val testUserType = UserType.Service
      val testMetadata = Json.obj("A" -> "BCDE", "B" -> 5)

      for {
        result <- (fixture.engine ? ExecuteWorkflow(
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
        users <- fixture.db.queryDatabase("LocalUser").map(_.containers.map(_.asInstanceOf[core.LocalUser]))
      } yield {
        result.wasSuccessful should be(true)
        users should have size 1
        users.head.userID should be(testUserID)
        users.head.hashedPassword should be(testHashedPassword)
        users.head.passwordSalt should be(testPasswordSalt)
        users.head.permissions should be(testPermissions)
        users.head.userType should be(testUserType)
        users.head.metadata should be(testMetadata)
      }
  }

  it should "fail to create multiple users with the same ID" in {
    fixture =>
      val testUserID = "test_uid_1"
      val testHashedPassword = "AbCdE123456!"
      val testPasswordSalt = "01232456_ABC"
      val testPermissions = Seq("A", "B", "C")
      val testUserType = UserType.Service
      val testMetadata = Json.obj("A" -> "BCDE", "B" -> 5)

      for {
        result1 <- (fixture.engine ? ExecuteWorkflow(
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
        result2 <- (fixture.engine ? ExecuteWorkflow(
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
        users <- fixture.db.queryDatabase("LocalUser").map(_.containers.map(_.asInstanceOf[core.LocalUser]))
      } yield {
        result1.wasSuccessful should be(true)
        result2.wasSuccessful should be(false)
        users should have size 1
        users.head.userID should be(testUserID)
        users.head.hashedPassword should be(testHashedPassword)
        users.head.passwordSalt should be(testPasswordSalt)
        users.head.permissions should be(testPermissions)
        users.head.userType should be(testUserType)
        users.head.metadata should be(testMetadata)
      }
  }
}
