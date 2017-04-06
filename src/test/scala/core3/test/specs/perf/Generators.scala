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
package core3.test.specs.perf

import core3.database.ContainerType
import core3.database.containers.core
import core3.database.dals.DatabaseAbstractionLayer
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object Generators {
  def createContainers(db: DatabaseAbstractionLayer, containerType: ContainerType, count: Int): Future[Unit] = {
    Future.sequence(
      0 until count map {
        i =>
          val container = containerType match {
            case "TransactionLog" =>
              new core.TransactionLog(
                "TestWorkflowName",
                core3.workflows.getNewRequestID,
                readOnlyWorkflow = true,
                Json.obj(),
                Json.obj(),
                "testUser",
                workflowResult = true,
                "testState"
              )

            case "Group" =>
              new core.Group(
                s"${i}_${count}_${core3.database.getNewObjectID.getMostSignificantBits}",
                "testName",
                Vector.empty,
                "TransactionLog",
                "test-user"
              )

            case _ => throw new IllegalArgumentException(s"specs.perf.Generators::createContainers > Unexpected container type encountered: [$containerType].")
          }

          db.createObject(container)
      }
    ).map {
      results =>
        results.indices.foreach {
          i =>
            assert(
              results(i),
              s"specs.perf.Generators::createContainers > Object creation failed at [$i] of [$count] for type [$containerType]."
            )
        }
    }
  }
}
