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

import core3.http.controllers.ClientControllerBase
import core3.http.handlers
import core3.security.NoAuthUserToken
import core3.utils.ActionGroup
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.Future

/**
  * Play controller definition for implementing user-facing services.
  * <br><br>
  * Notes:<br>
  * - Provides no authentication and authorization.<br>
  * - Only public actions are allowed.
  */
class ClientController extends ClientControllerBase[NoAuthUserToken] {
  private val auditLogger = Logger("audit")

  override def AuthorizedAction(
    requiredGroup: ActionGroup,
    okHandler: (Request[AnyContent], NoAuthUserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      auditLogger.error("core3.http.controllers.noauth.ClientController::AuthorizedAction > Action not supported.")
      handlers.JSON.internalError()
    }
  }

  override def PublicAction(
    okHandler: (Request[AnyContent],
      Option[NoAuthUserToken]) => Future[Result]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        auditLogger.warn(s"core3.http.controllers.noauth.ClientController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
          s"allowed for user [unknown] @ [${request.remoteAddress}].")

        okHandler(request, None)
    }
  }

  override def LoginAction(
    okHandler: (Request[AnyContent]) => Future[Result],
    unauthorizedHandler: (Request[AnyContent]) => Future[Result],
    loginHandler: (Request[AnyContent]) => Future[Result]
  ): Action[AnyContent] = {
    Action.async {
      auditLogger.error("core3.http.controllers.noauth.ClientController::LoginAction > Action not supported.")
      handlers.JSON.internalError()
    }
  }

  override def LogoutAction(returnToURI: Option[String]): Action[AnyContent] = {
    Action.async {
      auditLogger.error("core3.http.controllers.noauth.ClientController::LogoutAction > Action not supported.")
      handlers.JSON.internalError()
    }
  }
}
