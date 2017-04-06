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

import play.api.libs.json.{JsArray, JsNull, JsString, JsValue}

/**
  * User data container.
  *
  * @param idToken        the ID token, as returned by the auth provider
  * @param accessToken    the provider-specific access token
  * @param delegationData user delegation data, if any
  * @param profile        user profile data
  */
class Auth0UserToken(val idToken: String, val accessToken: String, val delegationData: Option[JsValue], val profile: JsValue) extends UserTokenBase {
  override val permissions: Vector[String] = (profile \ "permissions").getOrElse(JsArray()).as[Vector[String]]
  override val userID: String = (profile \ "user_id").as[String]
  val delegationIdToken: String = (delegationData.getOrElse(JsNull) \ "id_token").getOrElse(JsString("none")).as[String]
}
