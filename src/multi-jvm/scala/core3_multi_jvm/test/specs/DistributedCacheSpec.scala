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
package core3_multi_jvm.test.specs

import akka.remote.testconductor.RoleName
import akka.remote.testkit._
import akka.remote.transport.ThrottlerTransportAdapter
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import core3.database.containers.core
import core3.database.containers.core.TransactionLog
import core3.database.{ObjectID, getNewRevisionID, getNewRevisionSequenceNumber}
import core3.test.utils._
import core3_multi_jvm.test.utils.ClusterInitNode
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._

object DistributedCacheTestConfig extends MultiNodeConfig {

  import collection.JavaConverters._

  val baseConfig: Config = ConfigFactory.load()
    .withValue("akka.actor.provider", ConfigValueFactory.fromAnyRef("akka.cluster.ClusterActorRefProvider"))
    .withValue("akka.remote.artery.advanced.test-mode", ConfigValueFactory.fromAnyRef("on"))
    .withValue("akka.remote.netty.tcp.applied-adapters", ConfigValueFactory.fromIterable(Seq("trttl", "gremlin").asJava))

  commonConfig(baseConfig)

  val node1: RoleName = role("DistributedCacheNode1")
  val node2: RoleName = role("DistributedCacheNode2")
  val node3: RoleName = role("DistributedCacheNode3")

  nodeConfig(node1)(baseConfig)
  nodeConfig(node2)(baseConfig)
  nodeConfig(node3)(baseConfig)

  def getLocalNodeClusterPort(node: RoleName): Int = {
    node match {
      case x if x == node1 => 50001
      case x if x == node2 => 50002
      case x if x == node3 => 50003
    }
  }

  testTransport(on = true)
}

class DistributedCacheSpec extends MultiNodeSpec(DistributedCacheTestConfig) with UnitSpec {
  override def initialParticipants: Int = roles.size

  private val clusterPort = 50010
  private val localSource = core3.test.fixtures.Database.createMariaDBInstance()
  private var initNode: Option[ClusterInitNode] = None

  import DistributedCacheTestConfig._

  runOn(node1) {
    localSource.getSupportedContainers.foreach {
      objectType =>
        localSource.clearDatabaseStructure(objectType).ignoreFailure(printTrace = false).await(15.seconds)
        localSource.buildDatabaseStructure(objectType).await(15.seconds)
    }

    initNode = Some(new ClusterInitNode(clusterHost = "127.0.0.1", clusterPort, systemName = "core3_database_dals_memory__DistributedCache"))
    initNode.get.init()
  }

  enterBarrier("setup")

  private val localCache = core3.test.fixtures.Database.createDistributedCacheInstance(
    source = localSource,
    clusterPort = clusterPort,
    localPort = DistributedCacheTestConfig.getLocalNodeClusterPort(myself)
  )

  runOn(node1) {
    val expectedActiveNodes = initialParticipants
    waitUntil(what = "all nodes are active", waitTimeMs = 500, waitAttempts = 15) {
      initNode.get.activeNodesCount == expectedActiveNodes
    }
  }

  enterBarrier("cluster-join")

  "A DistributedCache cluster" should {
    "make changes to the database on one node and successfully propagate them to all nodes" in {
      enterBarrier("pre-test-01")
      val testLog1 = new TransactionLog("wfName#1", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog2 = new TransactionLog("wfName#2", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog3 = new TransactionLog("wfName#3", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
      val testLog4 = new TransactionLog("wfName#4", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")

      val emptyGroupPeople: Vector[ObjectID] = Vector.empty
      val nonEmptyGroupLogs = Vector[ObjectID](testLog1.id, testLog2.id)
      val testGroup1 = new core.Group("sname_1", "Group 1", emptyGroupPeople, "TransactionLog", "test-user")
      val testGroup2 = new core.Group("sname_2", "Group 2", nonEmptyGroupLogs, "TransactionLog", "test-user")
      val testGroup3 = new core.Group("sname_3", "Group 3", nonEmptyGroupLogs, "TransactionLog", "test-user")

      val created: Future[Seq[Boolean]] = if (isNode(node1)) {
        Future.sequence(
          Seq(
            localCache.createObject(testLog1),
            localCache.createObject(testLog2),
            localCache.createObject(testLog3),
            localCache.createObject(testLog4),
            localCache.createObject(testGroup1),
            localCache.createObject(testGroup2),
            localCache.createObject(testGroup3)
          )
        )
      } else {
        Future.successful(Seq.empty[Boolean])
      }

      (for {
        _ <- created
        _ <- waitUntilFuture(what = "all new objects are propagated", waitTimeMs = 1500, waitAttempts = 15) {
          for {
            logs <- localCache.queryDatabase("TransactionLog")
            groups <- localCache.queryDatabase("Group")
          } yield {
            logs.containers.size == 4 && groups.containers.size == 3
          }
        }
        logs <- localCache.queryDatabase("TransactionLog").map(_.containers.map(_.asInstanceOf[core.TransactionLog]))
        groups <- localCache.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        logs should have size 4
        logs.map(_.workflowName) should contain allOf(testLog1.workflowName, testLog2.workflowName, testLog3.workflowName, testLog4.workflowName)
        groups should have size 3
        groups.map(_.shortName) should contain allOf(testGroup1.shortName, testGroup2.shortName, testGroup3.shortName)
        groups
      }).flatMap {
        groups =>
          val newSourceNode = node3
          val updatedGroupName = "new group name"
          val updated: Future[Seq[Boolean]] = if (isNode(newSourceNode)) {
            val dbGroup1 = groups.filter(_.shortName == "sname_1").head
            val dbGroup2 = groups.filter(_.shortName == "sname_2").head
            dbGroup1.name = updatedGroupName
            dbGroup1.revision = getNewRevisionID
            dbGroup1.revisionNumber = getNewRevisionSequenceNumber(dbGroup1.revisionNumber)

            Future.sequence(
              Seq(
                localCache.updateObject(dbGroup1),
                localCache.deleteObject("Group", dbGroup2.id)
              )
            )
          } else {
            Future.successful(Seq.empty[Boolean])
          }

          for {
            _ <- updated
            _ <- waitUntilFuture(what = "all updates are propagated", waitTimeMs = 1500, waitAttempts = 15) {
              localCache.queryDatabase("Group").map {
                result =>
                  result.containers.size == 2 && result.containers.exists(_.asInstanceOf[core.Group].revisionNumber == 2)
              }
            }
            updatedGroups <- localCache.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
          } yield {
            updatedGroups should have size 2
            updatedGroups.map(_.shortName) should contain allOf("sname_1", "sname_3")
            updatedGroups.filter(_.shortName == "sname_1").head.name should equal(updatedGroupName)
          }
      }
    }

    "fail to propagate changes to nodes with connectivity issues" in {
      enterBarrier("pre-test-02")
      val updatedGroupName1 = "UGN_1"
      val updatedGroupName3 = "UGN_3"

      val updated: Future[Seq[Boolean]] = if (isNode(node1)) {
        (for {
          _ <- testConductor.blackhole(node1, node2, ThrottlerTransportAdapter.Direction.Both)

          groups <- localCache.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
        } yield {
          groups should have size 2
          val dbGroup1 = groups.filter(_.shortName == "sname_1").head
          val dbGroup3 = groups.filter(_.shortName == "sname_3").head
          val testGroup4 = new core.Group("sname_4", "Group 4", Vector.empty, "TransactionLog", "test-user")

          dbGroup1.name = updatedGroupName1
          dbGroup1.revision = getNewRevisionID
          dbGroup1.revisionNumber = getNewRevisionSequenceNumber(dbGroup1.revisionNumber)

          dbGroup3.name = updatedGroupName3
          dbGroup3.revision = getNewRevisionID
          dbGroup3.revisionNumber = getNewRevisionSequenceNumber(dbGroup3.revisionNumber)

          Future.sequence(
            Seq(
              localCache.updateObject(dbGroup1),
              localCache.updateObject(dbGroup3),
              localCache.createObject(testGroup4)
            )
          )
        }).flatMap(identity)
      } else {
        Future.successful(Seq.empty[Boolean])
      }

      for {
        _ <- updated
        _ <- waitUntilFuture(what = "all updates are propagated", waitTimeMs = 1500, waitAttempts = 15) {
          localCache.queryDatabase("Group").map {
            result =>
              myself match {
                case x if x == node1 => result.containers.size == 3
                case x if x == node2 => result.containers.size == 2
                case x if x == node3 => result.containers.size == 3
              }
          }
        }
        updatedGroups <- localCache.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        updatedGroups.size should be >= 2
        val dbGroup1 = updatedGroups.filter(_.shortName == "sname_1").head
        val dbGroup3 = updatedGroups.filter(_.shortName == "sname_3").head

        myself match {
          case x if x == node1 =>
            updatedGroups should have size 3
            val dbGroup4 = updatedGroups.filter(_.shortName == "sname_4").head

            dbGroup1.name should equal(updatedGroupName1)
            dbGroup3.name should equal(updatedGroupName3)
            dbGroup1.revisionNumber should be(3)
            dbGroup3.revisionNumber should be(2)
            dbGroup4.revisionNumber should be(1)

          case x if x == node2 =>
            updatedGroups should have size 2
            dbGroup1.name should not equal updatedGroupName1
            dbGroup3.name should not equal updatedGroupName3
            dbGroup1.revisionNumber should be(2)
            dbGroup3.revisionNumber should be(1)

          case x if x == node3 =>
            updatedGroups should have size 3
            val dbGroup4 = updatedGroups.filter(_.shortName == "sname_4").head

            dbGroup1.name should equal(updatedGroupName1)
            dbGroup3.name should equal(updatedGroupName3)
            dbGroup1.revisionNumber should be(3)
            dbGroup3.revisionNumber should be(2)
            dbGroup4.revisionNumber should be(1)
        }
      }
    }

    "successfully propagate changes to nodes that have recovered" in {
      enterBarrier("pre-test-03")
      val fixed: Future[Boolean] = if (isNode(node1)) {
        for {
          _ <- testConductor.passThrough(node1, node2, ThrottlerTransportAdapter.Direction.Both)
        } yield {
          true
        }
      } else {
        Future.successful(true)
      }

      for {
        _ <- fixed
        _ <- waitUntilFuture(what = "all updates are propagated", waitTimeMs = 1500, waitAttempts = 15) {
          localCache.queryDatabase("Group").map {
            result =>
              result.containers.size == 3
          }
        }
        updatedGroups <- localCache.queryDatabase("Group").map(_.containers.map(_.asInstanceOf[core.Group]))
      } yield {
        updatedGroups should have size 3
        val dbGroup1 = updatedGroups.filter(_.shortName == "sname_1").head
        val dbGroup3 = updatedGroups.filter(_.shortName == "sname_3").head
        val dbGroup4 = updatedGroups.filter(_.shortName == "sname_4").head

        dbGroup1.revisionNumber should be(3)
        dbGroup3.revisionNumber should be(2)
        dbGroup4.revisionNumber should be(1)
      }
    }
  }
}

class DistributedCacheMultiJvmNode1 extends DistributedCacheSpec

class DistributedCacheMultiJvmNode2 extends DistributedCacheSpec

class DistributedCacheMultiJvmNode3 extends DistributedCacheSpec
