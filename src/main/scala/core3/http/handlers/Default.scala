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

import play.api.mvc.{AnyContent, Request, Result, Results}

import scala.concurrent.Future

object Default {
  /**
    * Default handler for unauthorized (forbidden - 403) requests.
    *
    * @param request the incoming HTTP request
    * @return HTTP result
    */
  def forbidden(request: Request[AnyContent]): Future[Result] = {
    Future.successful(Results.Forbidden("403 - Not authorized to access resource"))
  }

  /**
    * Default handler for unauthenticated (unauthorized - 401) requests.
    *
    * @param request the incoming HTTP request
    * @return HTTP result
    */
  def unauthorized(request: Request[AnyContent]): Future[Result] = {
    Future.successful(Results.Unauthorized("401 - Not authenticated"))
  }

  /**
    * Default handler for requests when a service is unavailable / under maintenance (503).
    *
    * @return HTTP result
    */
  def unavailable(): Future[Result] = {
    Future.successful(Results.ServiceUnavailable("503 - Service temporarily unavailable"))
  }
}
