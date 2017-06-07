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
package core3.security

import core3.database.containers.core.LocalUser
import play.api.libs.json.JsValue

/**
  * User data container.
  *
  * @param userID       the ID of the user
  * @param permissions  the permissions the user has
  * @param profile      user profile data
  * @param sessionToken the session associated with the user
  */
class LocalAuthUserToken(val userID: String, val permissions: Vector[String], val profile: JsValue, val sessionToken: String) extends UserTokenBase {
}

object LocalAuthUserToken {
  def apply(userID: String, permissions: Vector[String], profile: JsValue, sessionToken: String): LocalAuthUserToken = new LocalAuthUserToken(userID, permissions, profile, sessionToken)

  def apply(user: LocalUser, sessionToken: String): LocalAuthUserToken = new LocalAuthUserToken(user.userID, user.permissions, user.metadata, sessionToken)
}
