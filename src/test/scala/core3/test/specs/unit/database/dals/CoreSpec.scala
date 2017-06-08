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
package core3.test.specs.unit.database.dals

import akka.pattern.ask
import akka.util.Timeout
import core3.database
import core3.database.containers.core
import core3.database.dals.{Core, DatabaseAbstractionLayer}
import core3.database.views
import core3.test.fixtures
import core3.test.specs.unit.AsyncUnitSpec
import core3.test.utils._
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._

class CoreSpec extends AsyncUnitSpec with CoreBehaviours {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest) = {
    val fixture = FixtureParam()
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  implicit val timeout = Timeout(15.seconds)
  val mariaDAL: DatabaseAbstractionLayer = fixtures.Database.createMariaDBInstance()
  val couchDAL: DatabaseAbstractionLayer = fixtures.Database.createCouchDBInstance()
  val memoryDAL: DatabaseAbstractionLayer = fixtures.Database.createMemoryOnlyDBInstance()
  val redisDAL: DatabaseAbstractionLayer = fixtures.Database.createRedisInstance()
  val elasticSearchDAL: DatabaseAbstractionLayer = fixtures.Database.createElasticSearchInstance()
  val coexistElasticSearchDAL: DatabaseAbstractionLayer = fixtures.Database.createElasticSearchInstance(coexist = true)
  val elasticStoreDAL: DatabaseAbstractionLayer = fixtures.Database.createElasticStoreInstance()
  val solrDAL: DatabaseAbstractionLayer = fixtures.Database.createSolrInstance()
  val distributedCacheDAL: DatabaseAbstractionLayer = fixtures.Database.createDistributedCacheInstance(mariaDAL)

  val mariaDBMasterMap = Map(
    "TransactionLog" -> Vector(mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef, redisDAL.getRef),
    "Group" -> Vector(mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef, redisDAL.getRef),
    "LocalUser" -> Vector(mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef, redisDAL.getRef)
  )

  val couchDBMasterMap = Map(
    "TransactionLog" -> Vector(couchDAL.getRef, memoryDAL.getRef, mariaDAL.getRef, redisDAL.getRef),
    "Group" -> Vector(couchDAL.getRef, memoryDAL.getRef, mariaDAL.getRef, redisDAL.getRef),
    "LocalUser" -> Vector(couchDAL.getRef, memoryDAL.getRef, mariaDAL.getRef, redisDAL.getRef)
  )

  val memoryDBMasterMap = Map(
    "TransactionLog" -> Vector(memoryDAL.getRef, mariaDAL.getRef, couchDAL.getRef, redisDAL.getRef),
    "Group" -> Vector(memoryDAL.getRef, mariaDAL.getRef, couchDAL.getRef, redisDAL.getRef),
    "LocalUser" -> Vector(memoryDAL.getRef, mariaDAL.getRef, couchDAL.getRef, redisDAL.getRef)
  )

  val redisMasterMap = Map(
    "TransactionLog" -> Vector(redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "Group" -> Vector(redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "LocalUser" -> Vector(redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef)
  )

  val elasticMasterMap = Map(
    "TransactionLog" -> Vector(elasticStoreDAL.getRef, redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "Group" -> Vector(elasticStoreDAL.getRef, redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "LocalUser" -> Vector(elasticStoreDAL.getRef, redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef)
  )

  val distributedCacheMasterMap = Map(
    "TransactionLog" -> Vector(distributedCacheDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "Group" -> Vector(distributedCacheDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "LocalUser" -> Vector(distributedCacheDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef)
  )

  "A Core with a MariaDB master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(mariaDBMasterMap), wipeData = false))

  it should "re-sync all TransactionLog container data successfully" in {
    _ =>
      import Core._

      val coreDAL = fixtures.Database.createCoreInstance(Some(mariaDBMasterMap))
      val testLog = core.TransactionLog("wfName#0", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      for {
        _ <- coreDAL.createObject(testLog)
        _ <- couchDAL.clearDatabaseStructure("TransactionLog").ignoreFailure
        _ <- couchDAL.buildDatabaseStructure("TransactionLog")
        _ <- memoryDAL.clearDatabaseStructure("TransactionLog").ignoreFailure
        _ <- memoryDAL.buildDatabaseStructure("TransactionLog")
        mariaDBQueryResult <- mariaDAL.queryDatabase("TransactionLog")
        couchDBQueryResult <- couchDAL.queryDatabase("TransactionLog")
        memoryDBQueryResult <- memoryDAL.queryDatabase("TransactionLog")
        _ <- Future {
          mariaDBQueryResult should contain(testLog)
          mariaDBQueryResult should have length 1
          couchDBQueryResult should have length 0
          memoryDBQueryResult should have length 0
        }
        syncResult <- (coreDAL.getRef ? SynchronizeDatabases(Some("TransactionLog"))).mapTo[Boolean]
        mariaDBQueryResult <- mariaDAL.queryDatabase("TransactionLog")
        couchDBQueryResult <- couchDAL.queryDatabase("TransactionLog")
        memoryDBQueryResult <- memoryDAL.queryDatabase("TransactionLog")
      } yield {
        syncResult should equal(true)
        mariaDBQueryResult should contain(testLog)
        mariaDBQueryResult should have length 1
        couchDBQueryResult should contain(testLog)
        couchDBQueryResult should have length 1
        memoryDBQueryResult should have length 1
        memoryDBQueryResult should contain(testLog)
      }
  }

  it should "be able to load views" in {
    _ =>
      val coreDAL = fixtures.Database.createCoreInstance(Some(mariaDBMasterMap))

      val testLog1 = core.TransactionLog("wfName#1", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog2 = core.TransactionLog("wfName#2", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog3 = core.TransactionLog("wfName#3", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog4 = core.TransactionLog("wfName#4", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")

      val nonEmptyGroupLogs: Vector[database.ObjectID] = Vector(testLog1.id, testLog2.id, testLog3.id)

      val testGroup4 = core.Group("sname_4", "Group 4", nonEmptyGroupLogs, "TransactionLog", "test-user")
      val testView1 = views.core.Group(testGroup4)

      val futures = Seq(
        coreDAL.createObject(testLog1),
        coreDAL.createObject(testLog2),
        coreDAL.createObject(testLog3),
        coreDAL.createObject(testLog4),
        coreDAL.createObject(testGroup4)
      )

      Future.sequence(futures).flatMap {
        _ =>
          for {
            _ <- coreDAL.loadView(testView1)
          } yield {
            testView1.items should have length 3
            testView1.items should contain(testLog1)
            testView1.items should contain(testLog2)
            testView1.items should contain(testLog3)
            testView1.items should not contain testLog4

            testView1.isAssociatedWith(testGroup4) should equal(true)
            testView1.isAssociatedWith(testLog1) should equal(true)
            testView1.isAssociatedWith(testLog2) should equal(true)
            testView1.isAssociatedWith(testLog3) should equal(true)
            testView1.isAssociatedWith(testLog4) should equal(false)
          }
      }
  }

  "A Core with a CouchDB master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(couchDBMasterMap), wipeData = false))

  "A Core with a MemoryOnlyDB master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(memoryDBMasterMap), wipeData = false))

  "A Core with a Redis master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(redisMasterMap), wipeData = false))

  "A Core with a ElasticSearch master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(elasticMasterMap), wipeData = false))

  "A Core with a DistributedCache master database" should behave like
    standardCoreDAL(fixtures.Database.createCoreInstance(Some(distributedCacheMasterMap), wipeData = false))

  it should "be able to load views" in {
    _ =>
      val coreDAL = fixtures.Database.createCoreInstance(Some(distributedCacheMasterMap))

      val testLog1 = core.TransactionLog("wfName#1", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog2 = core.TransactionLog("wfName#2", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog3 = core.TransactionLog("wfName#3", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog4 = core.TransactionLog("wfName#4", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")

      val nonEmptyGroupLogs: Vector[database.ObjectID] = Vector(testLog1.id, testLog2.id, testLog3.id)

      val testGroup4 = core.Group("sname_4", "Group 4", nonEmptyGroupLogs, "TransactionLog", "test-user")
      val testView1 = views.core.Group(testGroup4)

      val futures = Seq(
        coreDAL.createObject(testLog1),
        coreDAL.createObject(testLog2),
        coreDAL.createObject(testLog3),
        coreDAL.createObject(testLog4),
        coreDAL.createObject(testGroup4)
      )

      Future.sequence(futures).flatMap {
        _ =>
          for {
            _ <- coreDAL.loadView(testView1)
          } yield {
            testView1.items should have length 3
            testView1.items should contain(testLog1)
            testView1.items should contain(testLog2)
            testView1.items should contain(testLog3)
            testView1.items should not contain testLog4

            testView1.isAssociatedWith(testGroup4) should equal(true)
            testView1.isAssociatedWith(testLog1) should equal(true)
            testView1.isAssociatedWith(testLog2) should equal(true)
            testView1.isAssociatedWith(testLog3) should equal(true)
            testView1.isAssociatedWith(testLog4) should equal(false)
          }
      }
  }

  it should "start with no data, when preloading is disabled" in {
    _ =>
      val dcDAL = fixtures.Database.createDistributedCacheInstance(mariaDAL, preload = false)
      val coreDAL = fixtures.Database.createCoreInstance(Some(Map("TransactionLog" -> Vector(dcDAL.getRef))), wipeData = false)

      for {
        logs <- coreDAL.queryDatabase("TransactionLog")
      } yield {
        logs should have size 0
      }
  }

  it should "start with all available data, when preloading is enabled" in {
    _ =>
      val dcDAL = fixtures.Database.createDistributedCacheInstance(mariaDAL)
      val coreDAL = fixtures.Database.createCoreInstance(Some(Map("TransactionLog" -> Vector(dcDAL.getRef))), wipeData = false)

      for {
        _ <- waitUntilFuture(what = "preloading completes", waitTimeMs = 1500, waitAttempts = 15) {
          coreDAL.queryDatabase("TransactionLog").map {
            result =>
              result.size == 4
          }
        }
        logs <- coreDAL.queryDatabase("TransactionLog")
      } yield {
        logs should have size 4
      }
  }
}
