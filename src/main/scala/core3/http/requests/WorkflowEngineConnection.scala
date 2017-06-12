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
import core3.workflows.{WorkflowRequest, WorkflowResult}
import play.api.libs.json.{JsObject, JsValue}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A wrapper class for sending requests to [[core3.http.requests.ServiceConnectionComponentBase]] actors
  * communicating with workflow engine services.
  *
  * @param actor the actor to be used for all requests
  */
class WorkflowEngineConnection(private val actor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {

  import ServiceConnectionComponentBase._

  /**
    * Retrieves the underlying [[akka.actor.ActorRef]].
    *
    * @return the actor ref
    */
  def getRef: ActorRef = actor

  /**
    * Sends a workflow request to the (user-aware) service.
    *
    * @param user    the user token to send to the service
    * @param request the request to send
    * @return the result of the request
    */
  def post(user: UserTokenBase, request: WorkflowRequest): Future[WorkflowResult] = {
    (actor ? Post(Some(user), request.asJson.as[JsObject])).mapTo[(Int, JsValue)].map {
      case (_, result) => result.as[WorkflowResult]
    }
  }

  /**
    * Sends a workflow request to the (client-aware) service.
    *
    * @param request the request to send
    * @return the result of the request
    */
  def post(request: WorkflowRequest): Future[WorkflowResult] = {
    (actor ? Post(None, request.asJson.as[JsObject])).mapTo[(Int, JsValue)].map {
      case (_, result) => result.as[WorkflowResult]
    }
  }
}
