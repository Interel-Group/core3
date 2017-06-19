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
import core3.core.Component
import core3.core.Component.ExecuteAction
import core3.database._
import core3.database.containers._
import core3.database.dals.Core._
import core3.database.dals.DatabaseAbstractionLayer
import core3.utils.{Time, TimestampFormat}
import core3.utils.Time._
import core3.test.specs.unit.AsyncUnitSpec
import core3.workflows._
import play.api.libs.json.Json

import scala.concurrent.Future

trait CoreBehaviours {
  this: AsyncUnitSpec =>
  def standardCoreDAL(db: DatabaseAbstractionLayer)(implicit timeout: Timeout): Unit = {
    it should "clear all databases" in {
      _ =>
        (db.getRef ? ClearAllDatabases(ignoreErrors = true))
          .mapTo[Boolean]
          .map {
            result =>
              result should equal(true)
          }
    }

    it should "build all databases" in {
      _ =>
        (db.getRef ? BuildAllDatabases())
          .mapTo[Boolean]
          .map {
            result =>
              result should equal(true)
          }
    }

    it should "verify all databases" in {
      _ =>
        (db.getRef ? VerifyAllDatabases())
          .mapTo[Boolean]
          .map {
            result =>
              result should equal(true)
          }
    }

    it should "add, get and verify containers to all databases" in {
      _ =>
        val testLog1 = core.TransactionLog("wfName#1", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test", Time.getCurrentTimestamp.minusDays(5), getNewObjectID)
        val testLog2 = core.TransactionLog("wfName#2", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test", Time.getCurrentTimestamp.minusDays(3), getNewObjectID)
        val testLog3 = core.TransactionLog("wfName#3", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test", Time.getCurrentTimestamp.plusDays(5), getNewObjectID)
        val testLog4 = core.TransactionLog("wfName#4", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test", Time.getCurrentTimestamp.minusDays(15), getNewObjectID)

        val emptyGroupPeople: Vector[ObjectID] = Vector.empty
        val nonEmptyGroupLogs = Vector[ObjectID](testLog1.id, testLog2.id)
        val testGroup1 = core.Group("sname_1", "Group 1", emptyGroupPeople, "TransactionLog", "test-user")
        val testGroup2 = core.Group("sname_2", "Group 2", nonEmptyGroupLogs, "TransactionLog", "test-user")
        val testGroup3 = core.Group("sname_3", "Group 3", nonEmptyGroupLogs, "TransactionLog", "test-user")

        val emptyActionParams = Json.obj()
        val emptyActionDataContainers = Json.obj()
        val nonEmptyActionParams = Json.obj("init_param" -> 123, "string_param_1" -> "string_1", "init_param_2" -> 456, "string_param_2" -> "string_2")
        val nonEmptyActionDataContainers = Json.obj(testLog1.id.toString -> testLog1)
        val testTransactionLog1 = core.TransactionLog("Test Workflow 1", getNewRequestID, readOnlyWorkflow = true, emptyActionParams, emptyActionDataContainers, "test-user-ok", workflowResult = true, "TEST_1")
        val testTransactionLog2 = core.TransactionLog("Test Workflow 2", getNewRequestID, readOnlyWorkflow = true, nonEmptyActionParams, nonEmptyActionDataContainers, "test-user-ok", workflowResult = false, "TEST_2")
        val testTransactionLog3 = core.TransactionLog("Test Workflow 3", getNewRequestID, readOnlyWorkflow = false, nonEmptyActionParams, nonEmptyActionDataContainers, "test-user-ok", workflowResult = true, "TEST_3")
        val testTransactionLog4 = core.TransactionLog("Test Workflow 4", getNewRequestID, readOnlyWorkflow = false, emptyActionParams, emptyActionDataContainers, "test-user-ok", workflowResult = false, "TEST_4")

        val testLocalUser1 = core.LocalUser(s"testUser1", s"test password 1", s"test salt 1", Vector("A", "B"), core.LocalUser.UserType.Client, Json.obj("C" -> "D"), "test-user")
        val testLocalUser2 = core.LocalUser(s"testUser2", s"test password 2", s"test salt 2", Vector("C"), core.LocalUser.UserType.Service, Json.obj("E" -> 1), "test-user")
        val testLocalUser3 = core.LocalUser(s"testUser3", s"test password 3", s"test salt 3", Vector.empty, core.LocalUser.UserType.Client, Json.obj(), "test-user")

        val futures = Seq(
          db.createObject(testLog1),
          db.createObject(testLog2),
          db.createObject(testLog3),
          db.createObject(testLog4),
          db.createObject(testTransactionLog1),
          db.createObject(testTransactionLog2),
          db.createObject(testTransactionLog3),
          db.createObject(testTransactionLog4),
          db.createObject(testGroup1),
          db.createObject(testGroup2),
          db.createObject(testGroup3),
          db.createObject(testLocalUser1),
          db.createObject(testLocalUser2),
          db.createObject(testLocalUser3)
        )

        Future.sequence(futures).flatMap {
          _ =>
            for {
              dbLog1 <- db.getObject("TransactionLog", testLog1.id)
              dbLog2 <- db.getObject("TransactionLog", testLog2.id)
              dbLog3 <- db.getObject("TransactionLog", testLog3.id)
              dbLog4 <- db.getObject("TransactionLog", testLog4.id)

              dbTransactionLog1 <- db.getObject("TransactionLog", testTransactionLog1.id)
              dbTransactionLog2 <- db.getObject("TransactionLog", testTransactionLog2.id)
              dbTransactionLog3 <- db.getObject("TransactionLog", testTransactionLog3.id)
              dbTransactionLog4 <- db.getObject("TransactionLog", testTransactionLog4.id)

              dbGroup1 <- db.getObject("Group", testGroup1.id)
              dbGroup2 <- db.getObject("Group", testGroup2.id)
              dbGroup3 <- db.getObject("Group", testGroup3.id)

              dbUser1 <- db.getObject("LocalUser", testLocalUser1.id)
              dbUser2 <- db.getObject("LocalUser", testLocalUser2.id)
              dbUser3 <- db.getObject("LocalUser", testLocalUser3.id)
            } yield {
              testLog1 should equal(dbLog1)
              testLog2 should equal(dbLog2)
              testLog3 should equal(dbLog3)
              testLog4 should equal(dbLog4)

              testTransactionLog1 should equal(dbTransactionLog1)
              testTransactionLog2 should equal(dbTransactionLog2)
              testTransactionLog3 should equal(dbTransactionLog3)
              testTransactionLog4 should equal(dbTransactionLog4)

              testGroup1 should equal(dbGroup1)
              testGroup2 should equal(dbGroup2)
              testGroup3 should equal(dbGroup3)

              testLocalUser1 should equal(dbUser1)
              testLocalUser2 should equal(dbUser2)
              testLocalUser3 should equal(dbUser3)
            }
        }
    }

    it should "get all groups from the database, update one, store it, read it back and verify the changes" in {
      _ =>
        val newGroupName = "New Group Name #1"

        db.queryDatabase("Group")
          .flatMap {
            groups =>
              groups should have length 3
              groups.head.isInstanceOf[core.Group] should equal(true)

              val testGroup = groups.head.asInstanceOf[core.Group]
              testGroup.name = newGroupName

              testGroup.name should equal(newGroupName)

              for {
                updateResult <- db.updateObject(testGroup)
                dbGroup <- db.getObject("Group", testGroup.id)
              } yield {
                updateResult should equal(true)
                dbGroup should equal(testGroup)
              }
          }
    }

    it should "be able to query groups by short name" in {
      _ =>
        db.queryDatabase("Group", "getByShortName", Map("shortName" -> "sname_1")).map {
          groups =>
            groups should have length 1
            groups.head.asInstanceOf[core.Group].shortName should be("sname_1")
        }
    }

    it should "be able to query local users by user ID" in {
      _ =>
        db.queryDatabase("LocalUser", "getByUserID", Map("userID" -> "testUser2")).map {
          users =>
            users should have length 1
            users.head.asInstanceOf[core.LocalUser].userID should be("testUser2")
        }
    }

    it should "be able to query transaction logs between timestamps" in {
      _ =>
        db.queryDatabase(
          "TransactionLog",
          "getBetweenTimestamps",
          Map(
            "start" -> Time.getCurrentTimestamp.minusDays(6).toFormattedString(TimestampFormat.DefaultTimestamp),
            "end" -> Time.getCurrentTimestamp.minusDays(1).toFormattedString(TimestampFormat.DefaultTimestamp)
          )
        ).map {
          logs =>
            logs should have length 2
            val names = logs.map(_.asInstanceOf[core.TransactionLog].workflowName)
            names should contain("wfName#1")
            names should contain("wfName#2")
            names should not contain "wfName#3"
            names should not contain "wfName#4"
        }
    }

    it should "get all Groups from the database, delete two and verify the deletions" in {
      _ =>
        db.queryDatabase("Group")
          .flatMap {
            groups =>
              groups should have length 3

              var remainingEntriesIDs: Vector[ObjectID] = Vector.empty

              groups.foreach {
                currentEntry => {
                  currentEntry.isInstanceOf[core.Group] should equal(true)
                  remainingEntriesIDs :+= currentEntry.id
                }
              }

              remainingEntriesIDs should have length 3

              for {
                _ <- db.deleteObject("Group", remainingEntriesIDs(0))
                _ <- db.deleteObject("Group", remainingEntriesIDs(1))
                remainingGroups <- db.queryDatabase("Group")
              } yield {
                remainingEntriesIDs = remainingEntriesIDs.drop(2)


                remainingGroups.foreach {
                  currentContainer => remainingEntriesIDs should contain(currentContainer.id)
                }

                remainingGroups should have length 1
              }
          }
    }

    it should "fail when invalid custom queries are requested" in {
      _ =>
        recoverToSucceededIf[IllegalArgumentException] {
          db.queryDatabase("Group", "invalidQuery", Map.empty)
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.queryDatabase("LocalUser", "invalidQuery", Map.empty)
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.queryDatabase("TransactionLog", "invalidQuery", Map.empty)
        }
    }

    it should "fail to delete immutable containers" in {
      _ =>
        for {
          logs <- db.queryDatabase("TransactionLog")
          result <- db.deleteObject("TransactionLog", logs.head.id)
        } yield {
          result should be(false)
        }
    }

    it should "not process requests for databases that do not exist " in {
      _ =>
        recoverToSucceededIf[IllegalArgumentException] {
          db.clearDatabaseStructure("InvalidContainer")
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.buildDatabaseStructure("InvalidContainer")
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.verifyDatabaseStructure("InvalidContainer")
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.queryDatabase("InvalidContainer")
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.queryDatabase("InvalidContainer", "someQuery", Map.empty)
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.getObject("InvalidContainer", getNewObjectID)
        }

        recoverToSucceededIf[IllegalArgumentException] {
          db.deleteObject("InvalidContainer", getNewObjectID)
        }
    }

    it should "successfully process actions" in {
      _ =>
        db.getLayerType should be(dals.LayerType.Core)

        for {
          stats <- (db.getRef ? ExecuteAction("stats")).mapTo[Component.ActionResult]
          clear <- (db.getRef ? ExecuteAction("clear", Some(Map("objectsType" -> Some("ALL"))))).mapTo[Component.ActionResult]
          build <- (db.getRef ? ExecuteAction("build", Some(Map("objectsType" -> Some("all"))))).mapTo[Component.ActionResult]
          verify <- (db.getRef ? ExecuteAction("verify", Some(Map("objectsType" -> Some("all"))))).mapTo[Component.ActionResult]
          sync1 <- (db.getRef ? ExecuteAction("sync", Some(Map("objectsType" -> Some("all"))))).mapTo[Component.ActionResult]
          sync2 <- (db.getRef ? SynchronizeDatabases(None)).mapTo[Boolean]
          clearLogs <- (db.getRef ? ExecuteAction("clear", Some(Map("objectsType" -> Some("TransactionLog"))))).mapTo[Component.ActionResult]
          buildLogs <- (db.getRef ? ExecuteAction("build", Some(Map("objectsType" -> Some("TransactionLog"))))).mapTo[Component.ActionResult]
          verifyLogs <- (db.getRef ? ExecuteAction("verify", Some(Map("objectsType" -> Some("TransactionLog"))))).mapTo[Component.ActionResult]
          syncLogs <- (db.getRef ? ExecuteAction("sync", Some(Map("objectsType" -> Some("TransactionLog"))))).mapTo[Component.ActionResult]
        } yield {
          stats.wasSuccessful should be(true)
          stats.data should not be None
          stats.message should be(None)

          clear.wasSuccessful should be(true)
          clear.data should be(None)
          clear.message should be(None)

          build.wasSuccessful should be(true)
          build.data should be(None)
          build.message should be(None)

          verify.wasSuccessful should be(true)
          verify.data should be(None)
          verify.message should be(None)

          sync1.wasSuccessful should be(true)
          sync1.data should be(None)
          sync1.message should be(None)

          sync2 should be(true)

          clearLogs.wasSuccessful should be(true)
          clearLogs.data should be(None)
          clearLogs.message should be(None)

          buildLogs.wasSuccessful should be(true)
          buildLogs.data should be(None)
          buildLogs.message should be(None)

          verifyLogs.wasSuccessful should be(true)
          verifyLogs.data should be(None)
          verifyLogs.message should be(None)

          syncLogs.wasSuccessful should be(true)
          syncLogs.data should be(None)
          syncLogs.message should be(None)
        }
    }

    it should "fail to process actions with invalid parameters" in {
      _ =>
        for {
          clear1 <- (db.getRef ? ExecuteAction("clear", None)).mapTo[Component.ActionResult]
          clear2 <- (db.getRef ? ExecuteAction("clear", Some(Map("objectsType" -> None)))).mapTo[Component.ActionResult]
          clear3 <- (db.getRef ? ExecuteAction("clear", Some(Map.empty))).mapTo[Component.ActionResult]
          clear4 <- (db.getRef ? ExecuteAction("clear", Some(Map("objectsType" -> Some("all"))))).mapTo[Component.ActionResult]
          build1 <- (db.getRef ? ExecuteAction("build", None)).mapTo[Component.ActionResult]
          build2 <- (db.getRef ? ExecuteAction("build", Some(Map("objectsType" -> None)))).mapTo[Component.ActionResult]
          build3 <- (db.getRef ? ExecuteAction("build", Some(Map.empty))).mapTo[Component.ActionResult]
          verify1 <- (db.getRef ? ExecuteAction("verify", None)).mapTo[Component.ActionResult]
          verify2 <- (db.getRef ? ExecuteAction("verify", Some(Map("objectsType" -> None)))).mapTo[Component.ActionResult]
          verify3 <- (db.getRef ? ExecuteAction("verify", Some(Map.empty))).mapTo[Component.ActionResult]
          sync1 <- (db.getRef ? ExecuteAction("sync", None)).mapTo[Component.ActionResult]
          sync2 <- (db.getRef ? ExecuteAction("sync", Some(Map("objectsType" -> None)))).mapTo[Component.ActionResult]
          sync3 <- (db.getRef ? ExecuteAction("sync", Some(Map.empty))).mapTo[Component.ActionResult]
        } yield {
          clear1.wasSuccessful should be(false)
          clear1.data should be(None)
          clear1.message should not be None

          clear2.wasSuccessful should be(false)
          clear2.data should be(None)
          clear2.message should not be None

          clear3.wasSuccessful should be(false)
          clear3.data should be(None)
          clear3.message should not be None

          clear4.wasSuccessful should be(false)
          clear4.data should be(None)
          clear4.message should not be None

          build1.wasSuccessful should be(false)
          build1.data should be(None)
          build1.message should not be None

          build2.wasSuccessful should be(false)
          build2.data should be(None)
          build2.message should not be None

          build3.wasSuccessful should be(false)
          build3.data should be(None)
          build3.message should not be None

          verify1.wasSuccessful should be(false)
          verify1.data should be(None)
          verify1.message should not be None

          verify2.wasSuccessful should be(false)
          verify2.data should be(None)
          verify2.message should not be None

          verify3.wasSuccessful should be(false)
          verify3.data should be(None)
          verify3.message should not be None

          sync1.wasSuccessful should be(false)
          sync1.data should be(None)
          sync1.message should not be None

          sync2.wasSuccessful should be(false)
          sync2.data should be(None)
          sync2.message should not be None

          sync3.wasSuccessful should be(false)
          sync3.data should be(None)
          sync3.message should not be None
        }
    }
  }
}
