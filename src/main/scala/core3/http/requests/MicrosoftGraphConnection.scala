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
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A wrapper class for sending requests to [[core3.http.requests.ServiceConnectionComponentBase]] actors
  * communicating with Microsoft Graph (https://graph.microsoft.com) services.
  *
  * @param actor the actor to be used for all requests
  */
class MicrosoftGraphConnection(private val actor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {

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
    * @param graphURI the graph resource that will be used for the request (ex: /users)
    * @param graphParams the parameters to send, if any
    * @return the result of the request: (response code, response data)
    */
  def get(graphURI: String, graphParams: Option[JsObject] = None): Future[(Int, JsValue)] = {
    (actor ? Get(None, Json.obj("graphURI" -> graphURI, "graphParams" -> graphParams))).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a POST request to the service.
    *
    * @param graphURI the graph resource that will be used for the request (ex: /users)
    * @param graphParams the parameters to send, if any
    * @return the result of the request: (response code, response data)
    */
  def post(graphURI: String, graphParams: Option[JsObject] = None): Future[(Int, JsValue)] = {
    (actor ? Post(None, Json.obj("graphURI" -> graphURI, "graphParams" -> graphParams))).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a PUT request to the service.
    *
    * @param graphURI the graph resource that will be used for the request (ex: /users)
    * @param graphParams the parameters to send, if any
    * @return the result of the request: (response code, response data)
    */
  def put(graphURI: String, graphParams: Option[JsObject] = None): Future[(Int, JsValue)] = {
    (actor ? Put(None, Json.obj("graphURI" -> graphURI, "graphParams" -> graphParams))).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a DELETE request to the service.
    *
    * @param graphURI the graph resource that will be used for the request (ex: /users)
    * @param graphParams the parameters to send, if any
    * @return the result of the request: (response code, response data)
    */
  def delete(graphURI: String, graphParams: Option[JsObject] = None): Future[(Int, JsValue)] = {
    (actor ? Delete(None, Json.obj("graphURI" -> graphURI, "graphParams" -> graphParams))).mapTo[(Int, JsValue)]
  }

  /**
    * Sends a PATCH request to the service.
    *
    * @param graphURI the graph resource that will be used for the request (ex: /users)
    * @param graphParams the parameters to send, if any
    * @return the result of the request: (response code, response data)
    */
  def patch(graphURI: String, graphParams: Option[JsObject] = None): Future[(Int, JsValue)] = {
    (actor ? Patch(None, Json.obj("graphURI" -> graphURI, "graphParams" -> graphParams))).mapTo[(Int, JsValue)]
  }
}
