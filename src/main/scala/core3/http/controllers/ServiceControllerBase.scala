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
import core3.utils.ActionScope
import play.api.mvc._

import scala.concurrent.Future

/**
  * Play controller trait for implementing non-user services.
  */
trait ServiceControllerBase[T <: UserTokenBase] extends InjectedController {
  /**
    * User-aware action generator, for implementing actions that require both (non-interactive) client and user auth.
    *
    * @param requiredScope       the required client scope
    * @param okHandler           the handler to be executed if access to the resource is allowed
    * @param unauthorizedHandler the handler to be executed if access to the resource is not allowed
    * @param forbiddenHandler    the handler to be executed if the client does not have the required scope
    * @return the generated action
    */
  def UserAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], T) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]] = None,
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]] = None
  ): Action[AnyContent]

  /**
    * Client-aware action generator, for implementing actions that require client-only authentication and authorization.
    *
    * @param requiredScope       the required client scope
    * @param okHandler           the handler to be executed if access to the resource is allowed
    * @param unauthorizedHandler the handler to be executed if access to the resource is not allowed
    * @param forbiddenHandler    the handler to be executed if the client does not have the required scope
    * @return the generated action
    */
  def ClientAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], String) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]] = None,
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]] = None
  ): Action[AnyContent]

  /**
    * Public action generator, for implementing routes/services that do NOT require authentication and authorization.
    *
    * @param okHandler the handler to be executed
    * @return the generated action
    */
  def PublicAction(okHandler: (Request[AnyContent], Option[String]) => Future[Result]): Action[AnyContent]
}
