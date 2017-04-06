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
package core3.http.requests

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import core3.security.UserTokenBase
import play.api.libs.json.{JsObject, JsValue}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A wrapper class for sending requests to [[core3.http.requests.ServiceConnectionComponentBase]] actors.
  *
  * @param actor the actor to be used for all requests
  */
class ServiceConnection(private val actor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {

  import ServiceConnectionComponentBase._

  /**
    * Retrieves the underlying [[akka.actor.ActorRef]].
    *
    * @return the actor ref
    */
  def getRef: ActorRef = actor

  /**
    * Sends a GET request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return the result of the request: (response code, response data)
    */
  def get(user: Option[UserTokenBase], data: JsObject): Future[(Int, JsValue)] = {
    (actor ? Get(user, data)).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a POST request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return the result of the request: (response code, response data)
    */
  def post(user: Option[UserTokenBase], data: JsObject): Future[(Int, JsValue)] = {
    (actor ? Post(user, data)).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a PUT request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return the result of the request: (response code, response data)
    */
  def put(user: Option[UserTokenBase], data: JsObject): Future[(Int, JsValue)] = {
    (actor ? Put(user, data)).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a DELETE request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return the result of the request: (response code, response data)
    */
  def delete(user: Option[UserTokenBase], data: JsObject): Future[(Int, JsValue)] = {
    (actor ? Delete(user, data)).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a PATCH request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return the result of the request: (response code, response data)
    */
  def patch(user: Option[UserTokenBase], data: JsObject): Future[(Int, JsValue)] = {
    (actor ? Patch(user, data)).mapTo[(Int, JsValue)]
  }
}
