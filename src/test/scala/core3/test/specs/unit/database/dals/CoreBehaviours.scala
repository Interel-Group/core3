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
import core3.database.ObjectID
import core3.database.containers._
import core3.database.dals.Core._
import core3.database.dals.DatabaseAbstractionLayer
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
        val testLog1 = core.TransactionLog("wfName#1", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
        val testLog2 = core.TransactionLog("wfName#2", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
        val testLog3 = core.TransactionLog("wfName#3", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")
        val testLog4 = core.TransactionLog("wfName#4", java.util.UUID.randomUUID(), readOnlyWorkflow = false, Json.obj(), Json.obj(), "test-user", workflowResult = true, "test")

        val emptyGroupPeople: Vector[ObjectID] = Vector.empty
        val nonEmptyGroupLogs = Vector[ObjectID](testLog1.id, testLog2.id)
        val testGroup1 = core.Group("sname_1", "Group 1", emptyGroupPeople, "TransactionLog", "test-user")
        val testGroup2 = core.Group("sname_2", "Group 2", nonEmptyGroupLogs, "TransactionLog", "test-user")
        val testGroup3 = core.Group("sname_3", "Group 3", nonEmptyGroupLogs, "TransactionLog", "test-user")

        val emptyActionParams = Json.obj()
        val emptyActionDataContainers = Json.obj()
        val nonEmptyActionParams = Json.obj("init_param" -> 123, "string_param_1" -> "string_1", "init_param_2" -> 456, "string_param_2" -> "string_2")
        val nonEmptyActionDataContainers = Json.obj(testLog1.id.toString -> core.TransactionLog.toJsonData(testLog1))
        val testTransactionLog1 = core.TransactionLog("Test Workflow 1", getNewRequestID, readOnlyWorkflow = true, emptyActionParams, emptyActionDataContainers, "test-user-ok", workflowResult = true, "TEST_1")
        val testTransactionLog2 = core.TransactionLog("Test Workflow 2", getNewRequestID, readOnlyWorkflow = true, nonEmptyActionParams, nonEmptyActionDataContainers, "test-user-ok", workflowResult = false, "TEST_2")
        val testTransactionLog3 = core.TransactionLog("Test Workflow 3", getNewRequestID, readOnlyWorkflow = false, nonEmptyActionParams, nonEmptyActionDataContainers, "test-user-ok", workflowResult = true, "TEST_3")
        val testTransactionLog4 = core.TransactionLog("Test Workflow 4", getNewRequestID, readOnlyWorkflow = false, emptyActionParams, emptyActionDataContainers, "test-user-ok", workflowResult = false, "TEST_4")

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
          db.createObject(testGroup3)
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
  }
}
