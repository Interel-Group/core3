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
package core3.test.specs.perf.time.database.dals

import core3.database.ContainerType
import core3.database.dals.DatabaseAbstractionLayer
import core3.test.fixtures.TestSystem
import core3.test.specs.perf.{Generators, PerformanceTimeSpec}
import core3.test.utils._
import org.scalameter.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait CouchDBSpec extends PerformanceTimeSpec {
  implicit val waitDuration: FiniteDuration = 15.seconds
  implicit val ec: ExecutionContext = TestSystem.ec
  val maxContainers = 100
  val containerCount: Gen[Int] = Gen.range("containerCount")(from = 0, upto = maxContainers, hop = 10)

  performance of "CouchDB" in {
    measure method "createObject" in {
      using(containerCount)
        .config {
          dsl.curve -> "TransactionLog"
        }
        .beforeTests {
          CouchDBSpec.db.clearDatabaseStructure("TransactionLog").ignoreFailure.await
          CouchDBSpec.db.buildDatabaseStructure("TransactionLog").await
        }
        .in {
          count => CouchDBSpec.createContainers("TransactionLog", count).await
        }
    }

    measure method "queryDatabase" in {
      using(containerCount)
        .config {
          dsl.curve -> "TransactionLog"
        }
        .beforeTests {
          CouchDBSpec.db.clearDatabaseStructure("TransactionLog").ignoreFailure.await
          CouchDBSpec.db.buildDatabaseStructure("TransactionLog").await
          CouchDBSpec.createContainers("TransactionLog", maxContainers).await
        }
        .in {
          count =>
            0 until count foreach {
              _ =>
                val result = CouchDBSpec.db.queryDatabase("TransactionLog").await
                assert(result.size == maxContainers)
            }
        }
    }
  }
}

object CouchDBSpec {
  val db: DatabaseAbstractionLayer = core3.test.fixtures.Database.createCouchDBInstance()
  val core: DatabaseAbstractionLayer = core3.test.fixtures.Database.createCoreInstance(
    Some(
      Map(
        "TransactionLog" -> Vector(db.getRef),
        "Group" -> Vector(db.getRef)
      )
    ),
    wipeData = false
  )

  def createContainers(containerType: ContainerType, count: Int): Future[Unit] = {
    Generators.createContainers(db, containerType, count)
  }
}