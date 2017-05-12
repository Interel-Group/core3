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
package core3

import java.security.SecureRandom
import java.util.Base64

import com.roundeights.hasher.Implicits._
import com.typesafe.config.Config
import core3.database.containers.core.LocalUser
import core3.database.dals.DatabaseAbstractionLayer
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{ExecutionContext, Future}

package object security {
  /**
    * Generates a new random string with the specified size.
    *
    * @param withSize the size of the string to be generated
    * @param random   the RNG to be used
    * @return the requested string
    */
  def getRandomString(withSize: Int, random: SecureRandom): String = {
    BigInt(withSize, random).toString(32)
  }

  /**
    * Hashes the supplied password with a new random salt.
    *
    * @param rawPassword the password to hash
    * @param authConfig  authentication config object
    * @param random      the RNG to be used to generate the salt
    * @return (hashedPassword, randomSalt)
    */
  def hashPassword(rawPassword: String, authConfig: Config, random: SecureRandom): (String, String) = {
    val placeholderSaltSize = authConfig.getInt("saltSize")
    val randomSalt = getRandomString(placeholderSaltSize, random)
    (hashPassword(rawPassword, randomSalt, authConfig), randomSalt)
  }

  /**
    * Hashes the supplied password the the specified salt.
    *
    * @param rawPassword the password to hash
    * @param withSalt    the salt to be used for the hashing
    * @param authConfig  authentication config object
    * @return the hashed password
    */
  def hashPassword(rawPassword: String, withSalt: String, authConfig: Config): String = {
    val instanceSalt = authConfig.getString("instanceSalt")
    val passwordIterations = authConfig.getInt("passwordIterations")
    val keyLength = authConfig.getInt("keyLength")

    rawPassword.pbkdf2(withSalt + instanceSalt, passwordIterations, keyLength)
  }

  /**
    * Attempts to authenticate a user with the supplied credentials.
    *
    * @param userID     the ID of the user
    * @param password   the un-hashed user password
    * @param random     the RNG to be used for generating random strings
    * @param db         the DB to be used for performing the user query
    * @param authConfig authentication config object
    * @return Future[[core3.database.containers.core.LocalUser]] - the authenticated user, if the process is successful
    */
  def authenticateUser(userID: String, password: String, random: SecureRandom, db: DatabaseAbstractionLayer, authConfig: Config)(implicit ec: ExecutionContext): Future[LocalUser] = {
    val instanceSalt = authConfig.getString("instanceSalt")
    val passwordIterations = authConfig.getInt("passwordIterations")
    val keyLength = authConfig.getInt("keyLength")
    val placeholderPasswordSize = authConfig.getInt("placeholderPasswordSize")
    val placeholderSaltSize = authConfig.getInt("saltSize")

    for {
      user <- db.queryDatabase("LocalUser", "getByUserID", Map("userID" -> userID)).map(_.headOption.map(_.asInstanceOf[LocalUser]))
      salt <- Future.successful(user.map(_.passwordSalt).getOrElse(getRandomString(placeholderSaltSize, random)) + instanceSalt)
      hashedPassword <- Future {
        password.pbkdf2(salt, passwordIterations, keyLength)
      }
    } yield {
      if (hashedPassword.hash_=(user.map(_.hashedPassword).getOrElse(getRandomString(placeholderPasswordSize, random)))) {
        user.get
      } else {
        throw new RuntimeException("Failed to authenticate user")
      }
    }
  }

  /**
    * Attempts to retrieve user authentication credentials from the supplied HTTP request.
    *
    * @param request the request to get credentials from
    * @return the requested credentials, if they were made available
    */
  def getBasicAuthCredentials(request: Request[AnyContent]): Option[(String, String)] = {
    for {
      authHeader <- request.headers.get("Authorization")
      authData <- authHeader.split("Basic ").lastOption
      actualData <- Some(new String(Base64.getDecoder.decode(authData.getBytes("utf-8")), "utf-8").split(":"))
      user <- actualData.headOption
      password <- actualData.lastOption
    } yield {
      (user, password)
    }
  }
}
