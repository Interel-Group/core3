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
package core3.http.controllers.local

import com.typesafe.config.Config
import core3.database.containers.core.UserType
import core3.database.dals.DatabaseAbstractionLayer
import core3.http.controllers.ClientControllerBase
import core3.http.handlers.Default
import core3.security.LocalAuthUserToken
import core3.utils.ActionGroup
import play.api.cache.CacheApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Environment, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Play controller definition for implementing user-facing services.
  * <br><br>
  * Notes:
  * - Uses a local user database as an authentication and authorization provider.
  * - Provides action generators for user login and logout, public and secure services/routes.
  *
  * @param cache      the session cache to be used
  * @param authConfig authentication configuration
  * @param db         the database to be used for querying local users
  */
class ClientController(cache: CacheApi, authConfig: Config, db: DatabaseAbstractionLayer)
  (implicit environment: Environment, ec: ExecutionContext) extends ClientControllerBase[LocalAuthUserToken] {
  private val instanceSalt = authConfig.getString("instanceSalt")
  private val passwordIterations = authConfig.getInt("passwordIterations")
  private val keyLength = authConfig.getInt("keyLength")
  private val placeholderPasswordSize = authConfig.getInt("placeholderPasswordSize")
  private val placeholderSaltSize = authConfig.getInt("saltSize")
  private val sessionTokenSize = authConfig.getInt("sessionTokenSize")
  private val localTokenExpiration = authConfig.getInt("localTokenExpiration").minutes
  private val random = new java.security.SecureRandom
  private val auditLogger = Logger("audit")

  private def checkParameterSize(parameterName: String, paramSize: Int, minSize: Int): Unit = {
    if (paramSize <= minSize) {
      auditLogger.warn(s"core3.http.controllers.local.ClientController::() > The size of configuration parameter [$parameterName] may be too small for adequate security!")
    }
  }

  checkParameterSize("instanceSalt", instanceSalt.length, 64)
  checkParameterSize("passwordIterations", passwordIterations, 10000)
  checkParameterSize("keyLength", keyLength, 64)
  checkParameterSize("placeholderPasswordSize", placeholderPasswordSize, 64)
  checkParameterSize("saltSize", placeholderSaltSize, 64)
  checkParameterSize("sessionTokenSize", sessionTokenSize, 128)

  private val overrideConfig = if (authConfig.hasPath("override")) {
    Some(authConfig.getConfig("override"))
  } else {
    None
  }

  override def AuthorizedAction(
    requiredGroup: ActionGroup,
    okHandler: (Request[AnyContent], LocalAuthUserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val userToken = cache.get[LocalAuthUserToken](s"${request.session.get("sessionToken").getOrElse("")}_data")

        userToken match {
          case Some(token) =>
            //user authenticated
            if (token.permissions.contains(requiredGroup)) {
              //user action authorized
              auditLogger.info(s"core3.http.controllers.local.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
                s"with group [$requiredGroup] authorized for user [${token.userID}] @ [${request.remoteAddress}].")
              okHandler(request, token)
            } else {
              //user action not authorized
              auditLogger.error(s"core3.http.controllers.local.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
                s"with group [$requiredGroup] NOT authorized for user [${token.userID}] @ [${request.remoteAddress}].")

              forbiddenHandler match {
                case Some(handler) => handler(request)
                case None => Default.forbidden(request)
              }
            }

          case None =>
            //user not authenticated
            auditLogger.warn(s"core3.http.controllers.local.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
              s"with group [$requiredGroup] denied for user [unknown] @ [${request.remoteAddress}].")

            (overrideConfig, request.method) match {
              case (Some(config), "GET") =>
                core3.security.authenticateUser(config.getString("userID"), config.getString("password"), random, db, authConfig).flatMap {
                  user =>
                    if (user.userType == UserType.Client) {
                      val sessionToken = core3.security.getRandomString(sessionTokenSize, random)
                      val newToken = LocalAuthUserToken(user, sessionToken)
                      cache.set(s"${sessionToken}_data", newToken, localTokenExpiration)

                      //user authentication successful
                      auditLogger.warn(s"core3.http.controllers.local.ClientController::AuthorizedAction > " +
                        s"Override login via [${request.method}] @ [${request.uri}] " +
                        s"completed authentication for user [${newToken.userID}] @ [${request.remoteAddress}].")

                      okHandler(request, newToken).map(_.withSession("sessionToken" -> sessionToken))
                    } else {
                      throw new RuntimeException(s"User [${user.userID}] with unexpected type [${user.userType}] attempted login")
                    }
                }.recoverWith {
                  case NonFatal(e) =>
                    //user authentication failed
                    auditLogger.error(s"core3.http.controllers.local.ClientController::AuthorizedAction > Override login via " +
                      s"[${request.method}] @ [${request.uri}] failed authentication for user " +
                      s"[${config.getString("userID")}] @ [${request.remoteAddress}] with message [${e.getMessage}].")
                    e.printStackTrace()
                    unauthorizedHandler match {
                      case Some(handler) => handler(request)
                      case None => Default.unauthorized(request)
                    }
                }

              case _ =>
                unauthorizedHandler match {
                  case Some(handler) => handler(request)
                  case None => Default.unauthorized(request)
                }
            }
        }
    }
  }

  override def PublicAction(okHandler: (Request[AnyContent], Option[LocalAuthUserToken]) => Future[Result]): Action[AnyContent] = {
    Action.async {
      request =>
        val userToken = cache.get[LocalAuthUserToken](s"${request.session.get("sessionToken").getOrElse("")}_data")

        userToken match {
          case Some(token) =>
            auditLogger.info(s"core3.http.controllers.local.ClientController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for user [${token.userID}] @ [${request.remoteAddress}].")

          case None =>
            auditLogger.warn(s"core3.http.controllers.local.ClientController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for user [unknown] @ [${request.remoteAddress}].")
        }

        okHandler(request, userToken)
    }
  }

  override def LoginAction(
    okHandler: (Request[AnyContent]) => Future[Result],
    unauthorizedHandler: (Request[AnyContent]) => Future[Result],
    loginHandler: (Request[AnyContent]) => Future[Result]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val userToken = cache.get[LocalAuthUserToken](s"${request.session.get("sessionToken").getOrElse("")}_data")

        userToken match {
          case Some(token) =>
            //user authentication already completed
            auditLogger.warn(s"core3.http.controllers.local.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
              s"already completed authentication for user [${token.userID}] @ [${request.remoteAddress}].")
            okHandler(request)

          case None =>
            core3.security.getBasicAuthCredentials(request) match {
              case Some((userID, password)) =>
                core3.security.authenticateUser(userID, password, random, db, authConfig).flatMap {
                  user =>
                    if (user.userType == UserType.Client) {
                      val sessionToken = core3.security.getRandomString(sessionTokenSize, random)
                      val userToken = LocalAuthUserToken(user, sessionToken)
                      cache.set(s"${sessionToken}_data", userToken, localTokenExpiration)

                      //user authentication successful
                      auditLogger.info(s"core3.http.controllers.local.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                        s"completed authentication for user [${userToken.userID}] @ [${request.remoteAddress}].")
                      okHandler(request).map {
                        c => c.withSession("sessionToken" -> sessionToken)
                      }
                    } else {
                      throw new RuntimeException(s"User [${user.userID}] with unexpected type [${user.userType}] attempted login")
                    }
                }.recoverWith {
                  case NonFatal(e) =>
                    //user authentication failed
                    auditLogger.error(s"core3.http.controllers.local.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                      s"failed authentication for user [unknown] @ [${request.remoteAddress}] with message [${e.getMessage}].")
                    e.printStackTrace()
                    unauthorizedHandler(request)
                }

              case _ =>
                //user authentication required
                auditLogger.info(s"core3.http.controllers.local.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                  s"requires authentication for user [unknown] @ [${request.remoteAddress}].")
                loginHandler(request)
            }
        }
    }
  }

  override def LogoutAction(returnToURI: String): Action[AnyContent] = {
    Action {
      request =>
        request.session.get("sessionToken").flatMap {
          sessionToken => cache.get[LocalAuthUserToken](s"${sessionToken}_data")
        } foreach {
          userToken =>
            auditLogger.info(s"core3.http.controllers.local.ClientController::LogoutAction > Logout via [${request.method}] @ [${request.uri}] " +
              s"requested for user [${userToken.userID}] @ [${request.remoteAddress}].")
            cache.remove(s"${userToken.sessionToken}_data")
        }

        Redirect(returnToURI)
    }
  }
}
