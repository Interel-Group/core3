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
package core3.http.handlers

import akka.actor.ActorRef
import akka.util.Timeout
import core3.core.Component
import core3.core.cli.CommandRouter
import core3.http.responses.GenericResult
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

object JSON {
  /**
    * Handler for processing service management requests.
    *
    * @param manager actor reference for the current system manager
    * @param request the incoming HTTP request
    * @return HTTP result
    */
  def management(
    manager: ActorRef, auditLogger: Logger, request: Request[AnyContent]
  )(implicit ec: ExecutionContext, timeout: Timeout): Future[Result] = {
    val requestData = request.body.asJson.get
    val component = (requestData \ "component").as[String]
    val action = (requestData \ "action").as[String]
    val params = (requestData \ "params").asOpt[JsObject].map(_.value.map { case (k, v) => k -> v.asOpt[String] }.toMap)

    auditLogger.info(s"core3.http.handlers.Default::management > Accepted request for component [$component] and action [$action].")

    for {
      result <- CommandRouter.route(manager, component, action, params)
    } yield {
      auditLogger.info(s"core3.http.handlers.Default::management > Component [$component] completed action [$action].")
      Results.Ok(Json.toJson(result)(Component.writesActionResult))
    }
  }

  /**
    * Default handler for unauthorized (forbidden - 403) requests.
    *
    * @param request the incoming HTTP request
    * @return HTTP result
    */
  def forbidden(request: Request[AnyContent]): Future[Result] = {
    Future.successful(Results.Forbidden(GenericResult(wasSuccessful = false, message = Some("403 - Not authorized to access resource")).asJson))
  }

  /**
    * Default handler for unauthenticated (unauthorized - 401) requests.
    *
    * @param request the incoming HTTP request
    * @return HTTP result
    */
  def unauthorized(request: Request[AnyContent]): Future[Result] = {
    Future.successful(Results.Unauthorized(GenericResult(wasSuccessful = false, message = Some("401 - Not authenticated")).asJson))
  }

  /**
    * Default handler for requests when an internal error occurs (500).
    *
    * @return HTTP result
    */
  def internalError(): Future[Result] = {
    Future.successful(Results.InternalServerError(GenericResult(wasSuccessful = false, message = Some("500 - Internal server error")).asJson))
  }

  /**
    * Default handler for requests when a service is unavailable / under maintenance (503).
    *
    * @return HTTP result
    */
  def unavailable(): Future[Result] = {
    Future.successful(Results.ServiceUnavailable(GenericResult(wasSuccessful = false, message = Some("503 - Service temporarily unavailable")).asJson))
  }
}
