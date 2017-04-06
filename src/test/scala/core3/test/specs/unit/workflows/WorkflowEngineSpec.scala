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
package core3.test.specs.unit.workflows

import akka.actor.ActorRef
import akka.pattern.ask
import core3.database.containers.{JSONConverter, core}
import core3.database.dals.DatabaseAbstractionLayer
import core3.security.Auth0UserToken
import core3.test.fixtures.workflows._
import core3.test.fixtures.{Database, TestSystem}
import core3.test.specs.unit.AsyncUnitSpec
import core3.utils.Time._
import core3.utils.TimestampFormat
import core3.workflows.WorkflowEngineComponent._
import core3.workflows.{WorkflowEngineComponent, WorkflowResult}
import play.api.libs.json.{JsArray, Json}

class WorkflowEngineSpec extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  case class FixtureParam(engine: ActorRef, db: DatabaseAbstractionLayer, authorizedUser: Auth0UserToken, unauthorizedUser: Auth0UserToken)

  def withFixture(test: OneArgAsyncTest) = {
    val authorizedUser = core3.test.fixtures.Workflows.createAuthorizedUser()
    val unauthorizedUser = core3.test.fixtures.Workflows.createUnauthorizedUser()
    val db = Database.createCoreInstance()
    val engine = core3.test.fixtures.Workflows.createWorkflowEngine(db)

    val fixture = FixtureParam(engine, db, authorizedUser, unauthorizedUser)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  "A WorkflowEngineComponent" should "successfully execute workflows" in {
    fixture =>
      for {
        addGroups <- (fixture.engine ? ExecuteWorkflow(AddGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        actualGroupsBeforeUpdate <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
        updateGroups <- (fixture.engine ? ExecuteWorkflow(UpdateGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        getTransactionLogs <- (fixture.engine ? ExecuteWorkflow(
          QueryTransactionLogs.name,
          rawParams = Json.obj(
            "start" -> getCurrentTimestamp.minusMinutes(10).toFormattedString(TimestampFormat.DefaultTimestamp),
            "end" -> getCurrentTimestamp.plusMinutes(10).toFormattedString(TimestampFormat.DefaultTimestamp)
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        actualGroupsAfterUpdate <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
        actualTransactionLogs <- fixture.db.queryDatabase("TransactionLog").map(_.containers.map(_.asInstanceOf[core.TransactionLog]))
      } yield {
        addGroups.wasSuccessful should be(true)
        updateGroups.wasSuccessful should be(true)
        getTransactionLogs.wasSuccessful should be(true)

        getTransactionLogs.data should not be None

        actualGroupsAfterUpdate should not be empty
        actualTransactionLogs should not be empty

        val transactionLogs = (getTransactionLogs.data.get \ "logs").as[JsArray].value.map(c => JSONConverter.fromJsonData("TransactionLog", c).asInstanceOf[core.TransactionLog])

        actualGroupsBeforeUpdate.size should be(AddGroups.testGroupsCount)
        actualGroupsAfterUpdate.size should be(AddGroups.testGroupsCount)
        actualGroupsBeforeUpdate.forall(_.items.isEmpty) should be(true)
        actualGroupsAfterUpdate.forall(_.items.size == 1) should be(true)

        transactionLogs.forall(actualTransactionLogs.contains) should be(true)
      }
  }

  it should "fail to execute workflows that do not exist" in {
    fixture =>
      for {
        result1 <- (fixture.engine ? ExecuteWorkflow("invalid-workflow-1", rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        result2 <- (fixture.engine ? ExecuteWorkflow("invalid-workflow-2", rawParams = Json.obj("a" -> "b"), fixture.authorizedUser)).mapTo[WorkflowResult]
        result3 <- (fixture.engine ? ExecuteWorkflow("invalid-workflow-3", rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        result4 <- (fixture.engine ? ExecuteWorkflow("invalid-workflow-4", rawParams = Json.obj("c" -> 1), fixture.authorizedUser)).mapTo[WorkflowResult]
        result5 <- (fixture.engine ? ExecuteWorkflow("invalid-workflow-5", rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
      } yield {
        result1.wasSuccessful should be(false)
        result2.wasSuccessful should be(false)
        result3.wasSuccessful should be(false)
        result4.wasSuccessful should be(false)
        result5.wasSuccessful should be(false)
      }
  }

  it should "fail to execute disabled workflows" in {
    fixture =>
      for {
        _ <- fixture.engine ? SetWorkflowState(AddGroups.name, enabled = false)
        addGroups <- (fixture.engine ? ExecuteWorkflow(AddGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        actualGroups <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        addGroups.wasSuccessful should be(false)
        actualGroups.isEmpty should be(true)
      }
  }

  it should "fail to execute workflows requested by unauthorized users" in {
    fixture =>
      for {
        addGroups <- (fixture.engine ? ExecuteWorkflow(AddGroups.name, rawParams = Json.obj(), fixture.unauthorizedUser)).mapTo[WorkflowResult]
        actualGroups <- fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        addGroups.wasSuccessful should be(false)
        actualGroups.isEmpty should be(true)
      }
  }

  it should "fail to execute workflows when invalid parameters are supplied" in {
    fixture =>
      for {
        getTransactionLogs1 <- (fixture.engine ? ExecuteWorkflow(
          QueryTransactionLogs.name,
          rawParams = Json.obj(
            "start" -> 1,
            "end" -> getCurrentTimestamp.plusMinutes(10).toFormattedString(TimestampFormat.DefaultTimestamp)
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        getTransactionLogs2 <- (fixture.engine ? ExecuteWorkflow(
          QueryTransactionLogs.name,
          rawParams = Json.obj(
            "start" -> getCurrentTimestamp.minusMinutes(10).toFormattedString(TimestampFormat.DefaultTimestamp),
            "end" -> "yesterday"
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        getTransactionLogs3 <- (fixture.engine ? ExecuteWorkflow(
          QueryTransactionLogs.name,
          rawParams = Json.obj(
            "start" -> 3,
            "end" -> "tomorrow"
          ),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
        getTransactionLogs4 <- (fixture.engine ? ExecuteWorkflow(
          QueryTransactionLogs.name,
          rawParams = Json.obj(),
          fixture.authorizedUser
        )).mapTo[WorkflowResult]
      } yield {
        getTransactionLogs1.wasSuccessful should be(false)
        getTransactionLogs2.wasSuccessful should be(false)
        getTransactionLogs3.wasSuccessful should be(false)
        getTransactionLogs4.wasSuccessful should be(false)
      }
  }

  it should "fail to commit data when inconsistencies are present" in {
    fixture =>
      for {
        addGroups <- (fixture.engine ? ExecuteWorkflow(AddGroups.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
        result <- (fixture.engine ? ExecuteWorkflow(IncorrectGroupUpdate.name, rawParams = Json.obj(), fixture.authorizedUser)).mapTo[WorkflowResult]
      } yield {
        addGroups.wasSuccessful should be(true)
        result.wasSuccessful should be(false)
        result.message should not be None
      }
  }
}
