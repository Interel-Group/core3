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
package core3.test.specs.prop.workflows.definitions

import akka.actor.ActorRef
import akka.pattern.ask
import core3.database.containers.core
import core3.database.dals.{Core, DatabaseAbstractionLayer}
import core3.security.Auth0UserToken
import core3.test.fixtures.{Database, TestSystem}
import core3.test.specs.prop.{Generators, PropertySpec}
import core3.test.utils._
import core3.workflows.WorkflowEngineComponent.ExecuteWorkflow
import core3.workflows.WorkflowResult
import core3.workflows.definitions.SystemCreateGroup
import org.scalacheck.{Arbitrary, Gen, Shrink}
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration._

class WorkflowPropSpec_SystemCreateGroup extends PropertySpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout
  implicit override val generatorDrivenConfig = PropertyCheckConfiguration(minSuccessful = 100, maxDiscardedFactor = 10.0, workers = 2)
  implicit val noShrink: Shrink[String] = Shrink.shrinkAny
  implicit val waitDuration: FiniteDuration = 15.seconds

  private val workflows = Vector(SystemCreateGroup)
  private val authorizedUser = core3.test.fixtures.Workflows.createAuthorizedUser(workflows)
  private val db = Database.createCoreInstance()
  private val engine = core3.test.fixtures.Workflows.createWorkflowEngine(db, workflows, readOnlyTransactionLogsEnabled = false)

  case class FixtureParam(engine: ActorRef, db: DatabaseAbstractionLayer, authorizedUser: Auth0UserToken)

  def withFixture(test: OneArgTest) = {
    import Core._
    Await.result(
      for {
        _ <- db.getRef ? ClearAllDatabases(ignoreErrors = true)
        _ <- db.getRef ? BuildAllDatabases()
        _ <- db.getRef ? VerifyAllDatabases()
      } yield {
        true
      },
      atMost = 5.seconds
    )

    val fixture = FixtureParam(engine, db, authorizedUser)
    withFixture(test.toNoArgTest(fixture))
  }

  "A SystemCreateGroup workflow" should "successfully create groups" in {
    fixture =>
      forAll(
        (Arbitrary.arbitrary[String] suchThat (_.length > 0), "name"),
        (Arbitrary.arbitrary[String] suchThat (c => c.length > 0 && c.length < 32), "shortName"),
        (Generators.generateObjectIDsList, "items"),
        (Gen.oneOf("TransactionLog", "Group"), "itemsType")
      ) {
        (name, shortName, items, itemsType) =>
          val result = (fixture.engine ? ExecuteWorkflow(
            SystemCreateGroup.name,
            rawParams = Json.obj(
              "shortName" -> shortName,
              "name" -> name,
              "items" -> items,
              "itemsType" -> itemsType
            ), fixture.authorizedUser
          )).mapTo[WorkflowResult].await

          result.wasSuccessful should be(true)

          val groups = fixture.db.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group])).await
          groups should not be empty
          groups.filter(c => c.name == name && c.shortName == shortName && c.itemsType == itemsType) should have size 1
      }
  }
}
