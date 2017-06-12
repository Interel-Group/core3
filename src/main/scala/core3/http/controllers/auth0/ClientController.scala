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
package core3.http.controllers.auth0

import java.util.Base64
import javax.crypto.spec.SecretKeySpec

import com.typesafe.config.Config
import core3.http.controllers.ClientControllerBase
import core3.http.handlers.Default
import core3.security.Auth0UserToken
import core3.utils.ActionGroup
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtAlgorithm, JwtJson}
import play.api.cache.CacheApi
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Environment, Logger}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Play controller definition for implementing user-facing services.
  * <br><br>
  * Notes:
  * - Uses 'auth0' as an authentication and authorization provider.
  * - Provides action generators for user login and logout, public and secure services/routes.
  *
  * @param ws         web service client to use for making auth requests
  * @param cache      the session cache to be used
  * @param authConfig authentication configuration
  */
class ClientController(ws: WSClient, cache: CacheApi, authConfig: Config)
  (implicit environment: Environment, ec: ExecutionContext) extends ClientControllerBase[Auth0UserToken] {
  private val domain = authConfig.getString("domain")
  private val clientID = authConfig.getString("clientId")
  private val backendClientID = authConfig.getString("backendClientID")
  private val clientSecret = authConfig.getString("clientSecret")
  private val clientSecretAlgo = authConfig.getString("clientSecretAlgo")
  private val jwtAlgo = authConfig.getString("jwtAlgo")
  private val redirectURI = authConfig.getString("callbackURI")
  private val logoutReturnURI = authConfig.getString("logoutReturnURI")
  private val grantType = authConfig.getString("grantType")
  private val backendGrantType = authConfig.getString("backendGrantType")
  private val localTokenExpiration = authConfig.getInt("localTokenExpiration").minutes
  private val scope = authConfig.getString("scope")
  private val auditLogger = Logger("audit")

  private val overrideConfig = if (authConfig.hasPath("override")) {
    Some(authConfig.getConfig("override"))
  } else {
    None
  }

  /**
    * Checks if the supplied token is a valid and expected JWT.
    *
    * @param idToken       the token to be checked
    * @param secretEncoded set to true, if the secret is Base64 encoded (default is false)
    * @return true, if the token is valid
    */
  private def isIdTokenValid(idToken: String, secretEncoded: Boolean = false): Boolean = {
    val claim = if (secretEncoded) {
      JwtJson.decodeJson(
        idToken,
        new SecretKeySpec(Base64.getUrlDecoder.decode(clientSecret), clientSecretAlgo),
        Seq(JwtAlgorithm.fromString(jwtAlgo).asInstanceOf[JwtHmacAlgorithm])
      )
    } else {
      JwtJson.decodeJson(
        idToken,
        clientSecret,
        Seq(JwtAlgorithm.fromString(jwtAlgo).asInstanceOf[JwtHmacAlgorithm])
      )
    }
    claim.isSuccess
  }


  /**
    * Requests a raw JWT token from the auth provider, bypassing the normal user authentication procedure and using
    * the supplied override credentials.
    *
    * Note: Should only be used in development.
    *
    * @return the requested token's raw data: (ID token, access token)
    */
  private def getRawOverrideToken(config: Config): Future[(String, String)] = {
    val connection = config.getString("connection")
    val username = config.getString("username")
    val password = config.getString("password")

    auditLogger.warn(s"core3.http.controllers.auth0.ClientController::getRawOverrideToken > Override token requested for user [$username] @ [$connection].")

    val tokenResponse = ws.url(s"https://$domain/oauth/ro")
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .post(
        Json.obj(
          "client_id" -> clientID,
          "connection" -> connection,
          "scope" -> scope,
          "grant_type" -> "password",
          "username" -> username,
          "password" -> password
        )
      )

    tokenResponse.flatMap {
      response =>
        (for {
          idToken <- (response.json \ "id_token").asOpt[String]
          accessToken <- (response.json \ "access_token").asOpt[String]
        } yield {
          if (isIdTokenValid(idToken)) {
            Future.successful((idToken, accessToken))
          } else {
            Future.failed[(String, String)](
              new RuntimeException(s"core3.http.controllers.auth0.ClientController::getRawOverrideToken > Invalid token received.")
            )
          }
        }).getOrElse {
          Future.failed[(String, String)](
            new RuntimeException(s"core3.http.controllers.auth0.ClientController::getRawOverrideToken > Raw authentication token not sent by provider.")
          )
        }
    }
  }

  /**
    * Requests a raw JWT token from the auth provider.
    *
    * @param code the code to be sent to the provider
    * @return the requested token's raw data: (ID token, access token)
    */
  private def getRawToken(code: String): Future[(String, String)] = {
    val tokenResponse = ws.url(s"https://$domain/oauth/token")
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .post(
        Json.obj(
          "client_id" -> clientID,
          "client_secret" -> clientSecret,
          "redirect_uri" -> redirectURI,
          "code" -> code,
          "grant_type" -> grantType
        )
      )

    tokenResponse.flatMap {
      response =>
        (for {
          idToken <- (response.json \ "id_token").asOpt[String]
          accessToken <- (response.json \ "access_token").asOpt[String]
        } yield {
          if (isIdTokenValid(idToken)) {
            Future.successful((idToken, accessToken))
          } else {
            Future.failed[(String, String)](
              new RuntimeException(s"core3.http.controllers.auth0.ClientController::getRawToken > Invalid token received.")
            )
          }
        }).getOrElse {
          Future.failed[(String, String)](
            new RuntimeException(s"core3.http.controllers.auth0.ClientController::getRawToken > Raw authentication token not sent by provider.")
          )
        }
    }
  }

  /**
    * Requests and builds a user info token from the auth provider, based on the supplied ID and access tokens.
    *
    * @param idToken     the ID token associated with the user
    * @param accessToken the access token associated with the user
    * @return the requested user token
    */
  private def getUserToken(idToken: String, accessToken: String): Future[Auth0UserToken] = {
    for {
      userResponse <- ws.url(s"https://$domain/userinfo")
        .withQueryString("access_token" -> accessToken)
        .get()
      delegationResponse <- ws.url(s"https://$domain/delegation")
        .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
        .post(
          Json.obj(
            "client_id" -> clientID,
            "id_token" -> idToken,
            "target" -> backendClientID,
            "scope" -> scope,
            "api_type" -> "app",
            "grant_type" -> backendGrantType
          )
        )
    } yield {
      new Auth0UserToken(idToken, accessToken, Some(delegationResponse.json), userResponse.json)
    }
  }

  override def AuthorizedAction(
    requiredGroup: ActionGroup,
    okHandler: (Request[AnyContent], Auth0UserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent] => Future[Result])] = None,
    forbiddenHandler: Option[(Request[AnyContent] => Future[Result])] = None
  ): Action[AnyContent] = {
    Action.async {
      request =>
        (for {
          idToken <- request.session.get("idToken")
          userToken <- cache.get[Auth0UserToken](s"${idToken}_data")
        } yield {
          //user authenticated
          if (userToken.permissions.contains(requiredGroup)) {
            //user action authorized
            auditLogger.info(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
              s"with group [$requiredGroup] authorized for user [${userToken.userID}] @ [${request.remoteAddress}].")
            okHandler(request, userToken)
          } else {
            //user action not authorized
            auditLogger.error(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
              s"with group [$requiredGroup] NOT authorized for user [${userToken.userID}] @ [${request.remoteAddress}].")

            forbiddenHandler match {
              case Some(handler) => handler(request)
              case None => Default.forbidden(request)
            }
          }
        }).getOrElse {
          //user not authenticated
          auditLogger.warn(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
            s"with group [$requiredGroup] denied for user [unknown] @ [${request.remoteAddress}].")

          (overrideConfig, request.method) match {
            case (Some(config), "GET") =>
              getRawOverrideToken(config).flatMap {
                case (idToken, accessToken) =>
                  getUserToken(idToken, accessToken).flatMap {
                    userToken =>
                      cache.set(s"${idToken}_data", userToken, localTokenExpiration)
                      //user authentication successful
                      auditLogger.warn(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > " +
                        s"Override login via [${request.method}] @ [${request.uri}] " +
                        s"completed authentication for user [${userToken.userID}] @ [${request.remoteAddress}].")

                      if (userToken.permissions.contains(requiredGroup)) {
                        //user action authorized
                        auditLogger.info(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
                          s"with group [$requiredGroup] authorized for user [${userToken.userID}] @ [${request.remoteAddress}].")
                        okHandler(request, userToken).map(_.withSession("idToken" -> idToken))
                      } else {
                        //user action not authorized
                        auditLogger.error(s"core3.http.controllers.auth0.ClientController::AuthorizedAction > Access to [${request.method}] @ [${request.uri}] " +
                          s"with group [$requiredGroup] NOT authorized for user [${userToken.userID}] @ [${request.remoteAddress}].")

                        forbiddenHandler match {
                          case Some(handler) => handler(request)
                          case None => Default.forbidden(request)
                        }
                      }
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

  override def PublicAction(okHandler: (Request[AnyContent], Option[Auth0UserToken]) => Future[Result]): Action[AnyContent] = {
    Action.async {
      request =>
        val userToken = cache.get[Auth0UserToken](s"${request.session.get("idToken").getOrElse("")}_data")

        userToken match {
          case Some(token) =>
            auditLogger.info(s"core3.http.controllers.auth0.ClientController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for user [${token.userID}] @ [${request.remoteAddress}].")

          case None =>
            auditLogger.warn(s"core3.http.controllers.auth0.ClientController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
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
        val userToken = cache.get[Auth0UserToken](s"${request.session.get("idToken").getOrElse("")}_data")

        userToken match {
          case Some(token) =>
            //user authentication already completed
            auditLogger.warn(s"core3.http.controllers.auth0.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
              s"already completed authentication for user [${token.userID}] @ [${request.remoteAddress}].")
            okHandler(request)

          case None =>
            request.getQueryString("code") match {
              case Some(code) =>
                getRawToken(code).flatMap {
                  case (idToken, accessToken) =>
                    getUserToken(idToken, accessToken).flatMap {
                      userToken =>
                        cache.set(s"${idToken}_data", userToken, localTokenExpiration)
                        //user authentication successful
                        auditLogger.info(s"core3.http.controllers.auth0.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                          s"completed authentication for user [${userToken.userID}] @ [${request.remoteAddress}].")
                        okHandler(request).map {
                          c => c.withSession("idToken" -> idToken)
                        }
                    }
                }.recoverWith {
                  case NonFatal(e) =>
                    //user authentication failed
                    auditLogger.error(s"core3.http.controllers.auth0.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                      s"failed authentication for user [unknown] @ [${request.remoteAddress}] with message [${e.getMessage}].")
                    e.printStackTrace()
                    unauthorizedHandler(request)
                }

              case None =>
                //user authentication required
                auditLogger.info(s"core3.http.controllers.auth0.ClientController::LoginAction > Login via [${request.method}] @ [${request.uri}] " +
                  s"requires authentication for user [unknown] @ [${request.remoteAddress}].")
                loginHandler(request)
            }
        }
    }
  }

  override def LogoutAction(returnToURI: Option[String] = None): Action[AnyContent] = {
    Action {
      request =>
        request.session.get("idToken").flatMap {
          idToken => cache.get[Auth0UserToken](s"${idToken}_data")
        } foreach {
          userToken =>
            auditLogger.info(s"core3.http.controllers.auth0.ClientController::LogoutAction > Logout via [${request.method}] @ [${request.uri}] " +
              s"requested for user [${userToken.userID}] @ [${request.remoteAddress}].")
            cache.remove(s"${userToken.idToken}_data")
        }

        Redirect(
          s"https://$domain/v2/logout",
          Map(
            "returnTo" -> Seq(returnToURI.getOrElse(logoutReturnURI)),
            "federated" -> Seq(),
            "client_id" -> Seq(clientID)))
    }
  }
}
