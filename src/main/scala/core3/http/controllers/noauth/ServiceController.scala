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
package core3.http.controllers.noauth

import core3.http.controllers.ServiceControllerBase
import core3.http.handlers
import core3.security.NoAuthUserToken
import core3.utils.ActionScope
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.Future

/**
  * Play controller definition for implementing public-only non-user services.
  * <br><br>
  * Notes:<br>
  * - Provides no authentication and authorization.<br>
  * - Only public actions are allowed.
  */
class ServiceController extends ServiceControllerBase[NoAuthUserToken] {
  private val auditLogger = Logger("audit")

  override def UserAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], NoAuthUserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      auditLogger.error("core3.http.controllers.noauth.ServiceController::UserAwareAction > Action not supported.")
      handlers.JSON.internalError()
    }
  }

  override def ClientAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], String) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      auditLogger.error("core3.http.controllers.noauth.ServiceController::ClientAwareAction > Action not supported.")
      handlers.JSON.internalError()
    }
  }

  override def PublicAction(
    okHandler: (Request[AnyContent], Option[String]) => Future[Result]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        auditLogger.info(s"core3.http.controllers.noauth.ServiceController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
          s"allowed for client [unknown] @ [${request.remoteAddress}].")

        okHandler(request, None)
    }
  }
}
