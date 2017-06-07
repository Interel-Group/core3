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
package core3.test.specs.prop.database.dals

import core3.database.dals.DatabaseAbstractionLayer
import core3.test.fixtures
import core3.test.specs.prop.{Generators, PropertySpec}
import core3.test.utils._
import org.scalacheck.{Arbitrary, Shrink}

import scala.concurrent.duration._

class CoreSpec extends PropertySpec {

  case class FixtureParam(dbCore: DatabaseAbstractionLayer)

  def withFixture(test: OneArgTest) = {
    val coreDAL = fixtures.Database.createCoreInstance(dals = None)
    val fixture = FixtureParam(coreDAL)
    withFixture(test.toNoArgTest(fixture))
  }

  implicit override val generatorDrivenConfig = PropertyCheckConfiguration(minSuccessful = 100, maxDiscardedFactor = 10.0, workers = 2)
  implicit val noShrink: Shrink[String] = Shrink.shrinkAny
  implicit val waitDuration: FiniteDuration = 15.seconds

  "A DB Core" should "successfully create, store and retrieve 'TransactionLog' containers" in {
    fixture =>
      forAll((Generators.generateTransactionLog, "TransactionLog")) {
        (newLog) =>
          fixture.dbCore.createObject(newLog).await should equal(true)
          val queryResult = fixture.dbCore.queryDatabase("TransactionLog").await

          queryResult.size should be > 0
          queryResult should contain(newLog)
      }
  }

  it should "successfully create, store, retrieve, update and delete 'Group' containers" in {
    fixture =>
      forAll((Generators.generateGroup, "Group"), (Arbitrary.arbitrary[String] suchThat (_.length > 0), "UpdatedName")) {
        (newGroup, updatedName) =>
          fixture.dbCore.createObject(newGroup).await should equal(true)
          val queryResult = fixture.dbCore.queryDatabase("Group").await

          queryResult.size should be > 0
          queryResult should contain(newGroup)

          val updatedGroup = newGroup.copy(name = updatedName)
          fixture.dbCore.updateObject(updatedGroup).await should equal(true)
          val updatedQueryResult = fixture.dbCore.queryDatabase("Group").await

          updatedQueryResult.size should be > 0
          updatedQueryResult should not contain newGroup
          updatedQueryResult should contain(updatedGroup)

          fixture.dbCore.deleteObject("Group", newGroup.id).await should equal(true)
          val remainingQueryResult = fixture.dbCore.queryDatabase("Group").await
          remainingQueryResult should not contain newGroup
          remainingQueryResult should not contain updatedGroup
      }
  }

  it should "successfully create, store, retrieve, update and delete 'LocalUser' containers" in {
    fixture =>
      forAll((Generators.generateLocalUser, "LocalUser"), (Arbitrary.arbitrary[String] suchThat (_.length > 0), "UpdatedPassword")) {
        (newLocalUser, updatedPassword) =>
          fixture.dbCore.createObject(newLocalUser).await should equal(true)
          val queryResult = fixture.dbCore.queryDatabase("LocalUser").await

          queryResult.size should be > 0
          queryResult should contain(newLocalUser)

          val updatedLocalUser = newLocalUser.copy(hashedPassword = updatedPassword)
          fixture.dbCore.updateObject(updatedLocalUser).await should equal(true)
          val updatedQueryResult = fixture.dbCore.queryDatabase("LocalUser").await

          updatedQueryResult.size should be > 0
          updatedQueryResult should not contain newLocalUser
          updatedQueryResult should contain(updatedLocalUser)

          fixture.dbCore.deleteObject("LocalUser", newLocalUser.id).await should equal(true)
          val remainingQueryResult = fixture.dbCore.queryDatabase("LocalUser").await
          remainingQueryResult should not contain newLocalUser
          remainingQueryResult should not contain updatedLocalUser
      }
  }
}
