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
package core3.test.specs.unit.http.controllers

import java.security.SecureRandom
import java.util.Base64

import com.typesafe.config.Config
import core3.database.containers.core.LocalUser
import core3.database.dals.DatabaseAbstractionLayer
import core3.test.utils._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

package object local {
  def encodeCredentials(username: String, password: String): String = {
    Base64.getEncoder.encodeToString(s"$username:$password".getBytes("utf-8"))
  }

  def createUser(username: String, password: String, permissions: Vector[String], userType: LocalUser.UserType)
    (implicit db: DatabaseAbstractionLayer, authConfig: Config, random: SecureRandom, ec: ExecutionContext): Unit = {
    val (hashedPassword, passwordSalt) = core3.security.hashPassword(password, authConfig, random)

    db.createObject(
      LocalUser(
        username,
        hashedPassword,
        passwordSalt,
        permissions,
        userType,
        metadata = Json.obj(),
        "test-user"
      )
    ).map {
      result =>
        assert(result, "Failed to create local user for fixture")
    }.await
  }
}
