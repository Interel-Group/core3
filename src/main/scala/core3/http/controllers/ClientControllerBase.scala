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
package core3.http.controllers

import core3.security.UserTokenBase
import core3.utils.ActionGroup
import play.api.mvc._

import scala.concurrent.Future

/**
  * Play controller trait for implementing user-facing services.
  */
trait ClientControllerBase[T <: UserTokenBase] extends Controller {
  /**
    * Authorized action generator, for implementing routes/services that require authentication and authorization.
    *
    * @param requiredGroup       the group the user must be a member of in order to access the protected resource
    * @param okHandler           the handler to be executed if the user is allowed to access the resource
    * @param unauthorizedHandler the handler to be executed if the user has not authenticated
    * @param forbiddenHandler    the handler to be executed if the user is not allowed to access the resource
    * @return the generated action
    */
  def AuthorizedAction(
    requiredGroup: ActionGroup,
    okHandler: (Request[AnyContent], T) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent] => Future[Result])] = None,
    forbiddenHandler: Option[(Request[AnyContent] => Future[Result])] = None
  ): Action[AnyContent]

  /**
    * Public action generator, for implementing routes/services that do NOT require authentication and authorization.
    *
    * @param okHandler the handler to be executed
    * @return the generated action
    */
  def PublicAction(okHandler: (Request[AnyContent], Option[T]) => Future[Result]): Action[AnyContent]

  /**
    * Login action generator, for implementing user login handling.
    *
    * @param okHandler           the handler to execute after successful authentication/login or if the user is already logged-in
    * @param unauthorizedHandler the handler to execute if the user fails to authenticate/login
    * @param loginHandler        the handler to execute when the user needs to authenticate/login
    * @return the generated action
    */
  def LoginAction(
    okHandler: (Request[AnyContent]) => Future[Result],
    unauthorizedHandler: (Request[AnyContent]) => Future[Result],
    loginHandler: (Request[AnyContent]) => Future[Result]
  ): Action[AnyContent]

  /**
    * Logout action generator, for implementing user logout handling.
    *
    * @param returnToURI the URI to redirect to, after the logout has completed (if any); defaults based on implementation
    * @return the generated action
    */
  def LogoutAction(returnToURI: Option[String]): Action[AnyContent]
}
