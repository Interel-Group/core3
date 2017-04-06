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
package core3.test.specs.perf.time.workflows

import akka.pattern.ask
import core3.config.StaticConfig
import core3.database.containers.JSONConverter
import core3.test.fixtures
import core3.test.fixtures.TestSystem._
import core3.test.fixtures.workflows.AddGroups
import core3.test.specs.perf.PerformanceTimeSpec
import core3.test.utils._
import core3.workflows.WorkflowEngineComponent._
import org.scalameter.api._
import play.api.libs.json.Json

import scala.concurrent.duration._

object WorkflowEngineSpec extends PerformanceTimeSpec {
  implicit val waitDuration: FiniteDuration = 15.seconds
  val maxContainers = 1000
  val workflowsCount: Gen[Int] = Gen.range("workflowsCount")(from = 0, upto = maxContainers, hop = 100)

  private val db = fixtures.Database.createMemoryOnlyDBInstance()
  private val engine = fixtures.Workflows.createWorkflowEngine(db)
  private val authorizedUser = fixtures.Workflows.createAuthorizedUser()
  if (!JSONConverter.isInitialized) JSONConverter.initialize(core3.test.fixtures.Database.defaultJsonCompanions)

  private val resultDirConfigPath = "testing.reports.time.dsvPath"
  private val resultDir = if (StaticConfig.get.hasPath(resultDirConfigPath)) {
    StaticConfig.get.getString(resultDirConfigPath)
  } else {
    "target/specs.perf/time/dsv"
  }

  performance of "time.core3.database.dals" config(
    reports.resultDir -> resultDir,
    exec.benchRuns -> 10,
    exec.independentSamples -> 4,
    exec.warmupCovThreshold -> 0.5
  ) in {
    performance of "WorkflowEngineComponent" in {
      measure method "ExecuteWorkflow" in {
        using(workflowsCount)
          .config {
            dsl.curve -> "AddGroups"
          }
          .beforeTests {
            db.clearDatabaseStructure("Group").ignoreFailure.await
            db.buildDatabaseStructure("Group").await
            db.clearDatabaseStructure("TransactionLog").ignoreFailure.await
            db.buildDatabaseStructure("TransactionLog").await
          }
          .in {
            count =>
              (engine ? ExecuteWorkflow(AddGroups.name, Json.obj("count" -> count), authorizedUser)).await
          }
      }
    }
  }
}
