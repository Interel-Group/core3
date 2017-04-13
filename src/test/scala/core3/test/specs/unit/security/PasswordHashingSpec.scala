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
package core3.test.specs.unit.security

import java.security.SecureRandom

import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.database.containers.core
import core3.database.containers.core.UserType
import core3.database.dals.DatabaseAbstractionLayer
import core3.test.fixtures.{Database, TestSystem}
import core3.test.specs.unit.AsyncUnitSpec
import play.api.libs.json.Json

class PasswordHashingSpec extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  case class FixtureParam(db: DatabaseAbstractionLayer, random: SecureRandom, validConfig: Config, invalidConfig: Config)

  def withFixture(test: OneArgAsyncTest) = {
    val db = Database.createCoreInstance()
    val random = new SecureRandom()
    val validConfig = StaticConfig.get.getConfig("testing.security.valid")
    val invalidConfig = StaticConfig.get.getConfig("testing.security.invalid")

    val fixture = FixtureParam(db, random, validConfig, invalidConfig)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  "A set of password hashing functions" should "successfully hash and validate passwords" in {
    fixture =>
      val testUserID = "test-user-id#1"
      val testUserPassword = "test-user-password!"
      val testUserInvalidPassword = "test-user-password?"
      val (hashedPassword, passwordSalt) = core3.security.hashPassword(testUserPassword, fixture.validConfig, fixture.random)
      val hashedInvalidPassword = core3.security.hashPassword(testUserInvalidPassword, passwordSalt, fixture.validConfig)
      val hashedPasswordWithInvalidConfig = core3.security.hashPassword(testUserPassword, passwordSalt, fixture.invalidConfig)

      for {
        _ <- fixture.db.createObject(
          core.LocalUser(
            testUserID,
            hashedPassword,
            passwordSalt,
            permissions = Seq(),
            UserType.Service,
            metadata = Json.obj(),
            createdBy = "test-user"
          )
        )
        successfulResult <- core3.security.authenticateUser(testUserID, testUserPassword, fixture.random, fixture.db, fixture.validConfig)
        failedResult <- core3.security.authenticateUser(testUserID, testUserInvalidPassword, fixture.random, fixture.db, fixture.validConfig)
          .map(_ => "successful")
          .recover {
            case e: RuntimeException => e.getMessage
          }
        failedResultWithInvalidConfig <- core3.security.authenticateUser(testUserID, testUserPassword, fixture.random, fixture.db, fixture.invalidConfig)
          .map(_ => "successful")
          .recover {
            case e: RuntimeException => e.getMessage
          }
      } yield {
        hashedPassword should not be hashedInvalidPassword
        hashedPassword should not be hashedPasswordWithInvalidConfig
        hashedInvalidPassword should not be hashedPasswordWithInvalidConfig
        successfulResult.hashedPassword should be(hashedPassword)
        failedResult should be("Failed to authenticate user")
        failedResultWithInvalidConfig should be("Failed to authenticate user")
      }
  }
}
