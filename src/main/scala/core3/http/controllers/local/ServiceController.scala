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
import core3.database.containers.core.{LocalUser, UserType}
import core3.database.dals.DatabaseAbstractionLayer
import core3.http.controllers.ServiceControllerBase
import core3.http.handlers
import core3.security.LocalAuthUserToken
import core3.utils.ActionScope
import play.api.cache.CacheApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Environment, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Play controller definition for implementing non-user services.
  * <br><br>
  * Notes:
  * - Uses a local user database as an authentication and authorization provider.
  * - Provides action generators for user- and client-aware actions.
  *
  * @param cache      the session cache to be used\
  * @param authConfig authentication configuration
  * @param db         the database to be used for querying local users
  */
class ServiceController(cache: CacheApi, authConfig: Config, db: DatabaseAbstractionLayer)
  (implicit environment: Environment, ec: ExecutionContext) extends ServiceControllerBase[LocalAuthUserToken] {
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
      auditLogger.warn(s"core3.http.controllers.local.ServiceController::() > The size of configuration parameter [$parameterName] may be too small for adequate security!")
    }
  }

  checkParameterSize("instanceSalt", instanceSalt.length, 64)
  checkParameterSize("passwordIterations", passwordIterations, 10000)
  checkParameterSize("keyLength", keyLength, 64)
  checkParameterSize("placeholderPasswordSize", placeholderPasswordSize, 64)
  checkParameterSize("saltSize", placeholderSaltSize, 64)
  checkParameterSize("sessionTokenSize", sessionTokenSize, 128)

  override def UserAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], LocalAuthUserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val callUnauthorizedHandler: () => Future[Result] = () => {
          unauthorizedHandler match {
            case Some(handler) => handler(request)
            case None => handlers.JSON.unauthorized(request)
          }
        }

        val clientToken = cache.get[LocalAuthUserToken](s"${request.headers.get(core3.http.HeaderNames.CLIENT_SESSION_TOKEN).getOrElse("")}_data")
        val delegatedUserID = request.headers.get(core3.http.HeaderNames.USER_DELEGATION_TOKEN).getOrElse("None")

        clientToken match {
          case Some(token) =>
            //client authenticated
            if (token.permissions.contains(requiredScope)) {
              val cachedUserToken = cache.get[LocalAuthUserToken](s"${delegatedUserID}_data")

              cachedUserToken match {
                case Some(userToken) =>
                  auditLogger.info(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                    s"requested from client [${token.userID}] @ [${request.remoteAddress}] on behalf of user [${userToken.userID}] was accepted.")
                  okHandler(request, userToken)

                case None =>
                  val userQuery = db.queryDatabase("LocalUser", "getByUserID", Map("userID" -> delegatedUserID)).map {
                    result =>
                      result.containers.headOption match {
                        case Some(user) => user.asInstanceOf[LocalUser]
                        case None => throw new RuntimeException(s"Failed to retrieve user with id [$delegatedUserID]")
                      }
                  }

                  userQuery.flatMap {
                    user =>
                      //authentication successful
                      val userToken = LocalAuthUserToken(user, "none")
                      cache.set(s"${delegatedUserID}_data", userToken, localTokenExpiration)

                      auditLogger.info(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                        s"requested from client [${token.userID}] @ [${request.remoteAddress}] on behalf of user [${user.userID}] was accepted.")
                      okHandler(request, userToken)
                  }.recoverWith {
                    case NonFatal(e) =>
                      //user data is missing
                      auditLogger.error(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                        s"requested with missing user data from client [${token.userID}] @ [${request.remoteAddress}] on behalf of user [$delegatedUserID] failed with message [${e.getMessage}].")
                      callUnauthorizedHandler()
                  }
              }
            } else {
              //client not allowed to request action
              auditLogger.error(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [${token.userID}] @ [${request.remoteAddress}] on behalf of user [unknown] was not allowed.")

              forbiddenHandler match {
                case Some(handler) => handler(request)
                case None => handlers.JSON.forbidden(request)
              }
            }

          case None =>
            core3.security.getBasicAuthCredentials(request) match {
              case Some((userID, password)) =>
                core3.security.authenticateUser(userID, password, random, db, authConfig).flatMap {
                  client =>
                    if (client.userType == UserType.Service) {
                      val sessionToken = core3.security.getRandomString(sessionTokenSize, random)
                      val clientToken = LocalAuthUserToken(client, sessionToken)
                      cache.set(s"${sessionToken}_data", clientToken, localTokenExpiration)

                      val userQuery = db.queryDatabase("LocalUser", "getByUserID", Map("userID" -> delegatedUserID)).map {
                        result =>
                          result.containers.headOption match {
                            case Some(user) => user.asInstanceOf[LocalUser]
                            case None => throw new RuntimeException(s"Failed to retrieve user with id [$delegatedUserID]")
                          }
                      }

                      userQuery.flatMap {
                        user =>
                          //authentication successful
                          val userToken = LocalAuthUserToken(user, "none")
                          cache.set(s"${delegatedUserID}_data", userToken, localTokenExpiration)

                          auditLogger.info(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                            s"requested from client [${client.userID}] @ [${request.remoteAddress}] on behalf of user [${user.userID}] was accepted.")
                          okHandler(request, userToken).map {
                            c => c.withHeaders(core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> sessionToken)
                          }
                      }.recoverWith {
                        case NonFatal(e) =>
                          //user data is missing
                          auditLogger.error(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                            s"requested with missing user data from client [${client.userID}] @ [${request.remoteAddress}] on behalf of user [$delegatedUserID] failed with message [${e.getMessage}].")
                          callUnauthorizedHandler()
                      }
                    } else {
                      throw new RuntimeException(s"User [${client.userID}] with unexpected type [${client.userType}] attempted login")
                    }
                }.recoverWith {
                  case NonFatal(e) =>
                    //client authentication failed
                    auditLogger.error(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                      s"failed authentication for client [unknown] @ [${request.remoteAddress}] with message [${e.getMessage}].")
                    e.printStackTrace()
                    callUnauthorizedHandler()
                }

              case None =>
                //client auth data is missing
                auditLogger.error(s"core3.http.controllers.local.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                  s"requested with missing authentication data from client [unknown] @ [${request.remoteAddress}] on behalf of user [unknown].")
                callUnauthorizedHandler()
            }
        }
    }
  }

  override def ClientAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], String) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]],
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]]
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val callUnauthorizedHandler: () => Future[Result] = () => {
          unauthorizedHandler match {
            case Some(handler) => handler(request)
            case None => handlers.JSON.unauthorized(request)
          }
        }

        val clientToken = cache.get[LocalAuthUserToken](s"${request.headers.get(core3.http.HeaderNames.CLIENT_SESSION_TOKEN).getOrElse("")}_data")

        clientToken match {
          case Some(token) =>
            //client authenticated
            if (token.permissions.contains(requiredScope)) {
              //client allowed to request action
              auditLogger.info(s"core3.http.controllers.local.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [${token.userID}] @ [${request.remoteAddress}] was accepted.")
              okHandler(request, token.userID)
            } else {
              //client not allowed to request action
              auditLogger.error(s"core3.http.controllers.local.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [${token.userID}] @ [${request.remoteAddress}] was not allowed.")

              forbiddenHandler match {
                case Some(handler) => handler(request)
                case None => handlers.JSON.forbidden(request)
              }
            }

          case None =>
            core3.security.getBasicAuthCredentials(request) match {
              case Some((userID, password)) =>
                core3.security.authenticateUser(userID, password, random, db, authConfig).flatMap {
                  client =>
                    if (client.userType == UserType.Service) {
                      val sessionToken = core3.security.getRandomString(sessionTokenSize, random)
                      val clientToken = LocalAuthUserToken(client, sessionToken)
                      cache.set(s"${sessionToken}_data", clientToken, localTokenExpiration)

                      auditLogger.info(s"core3.http.controllers.local.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                        s"requested from client [${client.userID}] @ [${request.remoteAddress}] on behalf of user [${client.userID}] was accepted.")
                      okHandler(request, client.userID).map {
                        c => c.withHeaders(core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> sessionToken)
                      }
                    } else {
                      throw new RuntimeException(s"User [${client.userID}] with unexpected type [${client.userType}] attempted login")
                    }
                }.recoverWith {
                  case NonFatal(e) =>
                    //client authentication failed
                    auditLogger.error(s"core3.http.controllers.local.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                      s"failed authentication for client [unknown] @ [${request.remoteAddress}] with message [${e.getMessage}].")
                    e.printStackTrace()
                    callUnauthorizedHandler()
                }

              case None =>
                //client auth data is missing
                auditLogger.error(s"core3.http.controllers.local.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                  s"requested with missing client token from client [unknown] @ [${request.remoteAddress}].")
                callUnauthorizedHandler()
            }
        }
    }
  }

  override def PublicAction(okHandler: (Request[AnyContent], Option[String]) => Future[Result]): Action[AnyContent] = {
    Action.async {
      request =>
        val clientID = cache.get[LocalAuthUserToken](s"${request.headers.get(core3.http.HeaderNames.CLIENT_SESSION_TOKEN).getOrElse("")}_data").map(_.userID)

        clientID match {
          case Some(id) =>
            auditLogger.info(s"core3.http.controllers.local.ServiceController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for client [$id] @ [${request.remoteAddress}].")

          case None =>
            auditLogger.info(s"core3.http.controllers.local.ServiceController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for client [unknown] @ [${request.remoteAddress}].")
        }

        okHandler(request, clientID)
    }
  }
}
