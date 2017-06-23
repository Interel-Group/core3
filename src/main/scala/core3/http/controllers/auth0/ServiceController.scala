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

import com.typesafe.config.Config
import core3.http.controllers.ServiceControllerBase
import core3.http.handlers
import core3.security.Auth0UserToken
import core3.utils.ActionScope
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtAlgorithm, JwtJson, JwtOptions}
import play.api.cache.CacheApi
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Environment, Logger}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Play controller definition for implementing non-user services.
  * <br><br>
  * Notes:
  * - Uses 'auth0' as an authentication and authorization provider.
  * - Provides action generators for user- and client-aware actions.
  *
  * @param ws            web service client to use for making auth requests
  * @param cache         the session cache to be used
  * @param serviceConfig service configuration
  * @param authConfig    authentication configuration
  */
class ServiceController(ws: WSClient, cache: CacheApi, serviceConfig: Config, authConfig: Config)
  (implicit environment: Environment, ec: ExecutionContext) extends ServiceControllerBase[Auth0UserToken] {
  private val domain = authConfig.getString("domain")
  private val clientSecret = authConfig.getString("clientSecret")
  private val clientJwtAlgo = authConfig.getString("jwtAlgo")
  private val serviceSecret = serviceConfig.getString("secret")
  private val serviceJwtAlgo = serviceConfig.getString("jwtAlgo")
  private val localUserTokenExpiration = serviceConfig.getInt("localUserTokenExpiration").minutes
  private val auditLogger = Logger("audit")

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
    } yield {
      new Auth0UserToken(idToken, accessToken, None, userResponse.json)
    }
  }

  /**
    * Checks if the supplied token is valid.
    *
    * @param secret        the secret to be used for validating
    * @param token         the token to be validated
    * @param tokenAlgo     the algorithm used for generating the token
    * @return true if the token is valid
    */
  private def isTokenValid(secret: String, token: String, tokenAlgo: String): Boolean = {
    val claim =
      JwtJson.decodeJson(
        token,
        secret,
        Seq(JwtAlgorithm.fromString(tokenAlgo).asInstanceOf[JwtHmacAlgorithm])
      )

    claim.isSuccess
  }

  override def UserAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], Auth0UserToken) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]] = None,
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]] = None
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val callUnauthorizedHandler: () => Future[Result] = () => {
          unauthorizedHandler match {
            case Some(handler) => handler(request)
            case None => handlers.JSON.unauthorized(request)
          }
        }

        val delegationIdToken = request.headers.get(core3.http.HeaderNames.USER_DELEGATION_TOKEN).getOrElse("None")
        val accessToken = request.headers.get(core3.http.HeaderNames.USER_ACCESS_TOKEN).getOrElse("None")
        val clientToken = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse("").split("Bearer ").last

        if (delegationIdToken == "None" || accessToken == "None" || clientToken.isEmpty) {
          //one or more required tokens are missing
          auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
            s"requested with one or more missing tokens from client [unknown] @ [${request.remoteAddress}] on behalf of user [unknown].")
          callUnauthorizedHandler()
        } else {
          //all tokens are supplied
          if (!isTokenValid(serviceSecret, clientToken, serviceJwtAlgo)) {
            //client token is invalid
            auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
              s"requested with invalid client token from client [unknown] @ [${request.remoteAddress}] on behalf of user [unknown].")
            callUnauthorizedHandler()
          } else {
            //client token is valid
            val decodedClientToken = JwtJson.decodeJson(clientToken, JwtOptions(signature = false)).get
            val allowedClientScopes = (decodedClientToken \ "scope").getOrElse(JsString("")).as[String].split(" ")
            val clientID = (decodedClientToken \ "sub").getOrElse(JsString("unknown")).as[String]

            if (!allowedClientScopes.contains(requiredScope)) {
              //client not allowed to request action
              auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [$clientID] @ [${request.remoteAddress}] on behalf of user [unknown] was not allowed.")

              forbiddenHandler match {
                case Some(handler) => handler(request)
                case None => handlers.JSON.forbidden(request)
              }
            } else {
              //client allowed to request action
              if (!isTokenValid(clientSecret, delegationIdToken, clientJwtAlgo)) {
                //user delegation token is invalid
                auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                  s"requested with invalid user delegation token from client [$clientID] @ [${request.remoteAddress}] on behalf of user [unknown].")
                callUnauthorizedHandler()
              } else {
                val cachedUserToken = cache.get[Auth0UserToken](s"${accessToken}_data")
                if (cachedUserToken.isEmpty) {
                  //no user token was found
                  getUserToken(delegationIdToken, accessToken).flatMap {
                    userToken =>
                      val decodedDelegationToken = JwtJson.decodeJson(delegationIdToken, JwtOptions(signature = false)).get
                      val expectedUserID = (decodedDelegationToken \ "sub").getOrElse(JsString("unknown")).as[String]
                      if (userToken.userID != expectedUserID) {
                        //user profile retrieval failed
                        auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                          s"requested from client [$clientID] @ [${request.remoteAddress}] on behalf of user [$expectedUserID] retrieved profile for incorrect user: [${userToken.userID}].")
                        callUnauthorizedHandler()
                      } else {
                        cache.set(s"${accessToken}_data", userToken, localUserTokenExpiration)
                        auditLogger.info(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                          s"requested from client [$clientID] @ [${request.remoteAddress}] on behalf of user [${userToken.userID}] was accepted.")
                        okHandler(request, userToken)
                      }
                  }.recoverWith {
                    case NonFatal(e) =>
                      //user profile retrieval failed
                      auditLogger.error(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                        s"requested from client [$clientID] @ [${request.remoteAddress}] on behalf of user [unknown] failed to retrieve user profile data.")
                      e.printStackTrace()
                      callUnauthorizedHandler()
                  }
                } else {
                  auditLogger.info(s"core3.http.controllers.auth0.ServiceController::UserAwareAction > Action [${request.method}] @ [${request.uri}] " +
                    s"requested from client [$clientID] @ [${request.remoteAddress}] on behalf of user [${cachedUserToken.get.userID}] was accepted.")
                  okHandler(request, cachedUserToken.get)
                }
              }
            }
          }
        }
    }
  }

  override def ClientAwareAction(
    requiredScope: ActionScope,
    okHandler: (Request[AnyContent], String) => Future[Result],
    unauthorizedHandler: Option[(Request[AnyContent]) => Future[Result]] = None,
    forbiddenHandler: Option[(Request[AnyContent]) => Future[Result]] = None
  ): Action[AnyContent] = {
    Action.async {
      request =>
        val callUnauthorizedHandler: () => Future[Result] = () => {
          unauthorizedHandler match {
            case Some(handler) => handler(request)
            case None => handlers.JSON.unauthorized(request)
          }
        }

        val delegationIdToken = request.headers.get(core3.http.HeaderNames.USER_DELEGATION_TOKEN).getOrElse("None")
        val accessToken = request.headers.get(core3.http.HeaderNames.USER_ACCESS_TOKEN).getOrElse("None")
        val clientToken = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse("").split("Bearer ").last

        if (delegationIdToken != "None") {
          //user tokens should not be provided
          auditLogger.warn(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
            s"requested with user delegation token from client [unknown] @ [${request.remoteAddress}]; token not needed.")
        }

        if (accessToken != "None") {
          //user tokens should not be provided
          auditLogger.warn(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
            s"requested with access token from client [unknown] @ [${request.remoteAddress}]; token not needed.")
        }

        if (clientToken.isEmpty) {
          //client token is missing
          auditLogger.error(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
            s"requested with missing client token from client [unknown] @ [${request.remoteAddress}].")
          callUnauthorizedHandler()
        } else {
          //client token is supplied
          if (!isTokenValid(serviceSecret, clientToken, serviceJwtAlgo)) {
            //client token is invalid
            auditLogger.error(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
              s"requested with invalid client token from client [unknown] @ [${request.remoteAddress}].")
            callUnauthorizedHandler()
          } else {
            //client token is valid
            val decodedClientToken = JwtJson.decodeJson(clientToken, JwtOptions(signature = false)).get
            val allowedClientScopes = (decodedClientToken \ "scope").getOrElse(JsString("")).as[String].split(" ")
            val clientID = (decodedClientToken \ "sub").getOrElse(JsString("unknown")).as[String]

            if (!allowedClientScopes.contains(requiredScope)) {
              //client not allowed to request action
              auditLogger.error(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [$clientID] @ [${request.remoteAddress}] was not allowed.")

              forbiddenHandler match {
                case Some(handler) => handler(request)
                case None => handlers.JSON.forbidden(request)
              }
            } else {
              //client allowed to request action
              auditLogger.info(s"core3.http.controllers.auth0.ServiceController::ClientAwareAction > Action [${request.method}] @ [${request.uri}] " +
                s"requested from client [$clientID] @ [${request.remoteAddress}] was accepted.")
              okHandler(request, clientID)
            }
          }
        }
    }
  }

  override def PublicAction(okHandler: (Request[AnyContent], Option[String]) => Future[Result]): Action[AnyContent] = {
    Action.async {
      request =>
        val clientID = JwtJson.decodeJson(
          request.headers.get(HeaderNames.AUTHORIZATION).getOrElse("").split("Bearer ").last,
          serviceSecret,
          Seq(JwtAlgorithm.fromString(serviceJwtAlgo).asInstanceOf[JwtHmacAlgorithm])
        ).toOption.map {
          token =>
            (token \ "sub").getOrElse(JsString("unknown")).as[String]
        }

        clientID match {
          case Some(id) =>
            auditLogger.info(s"core3.http.controllers.auth0.ServiceController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for client [$id] @ [${request.remoteAddress}].")

          case None =>
            auditLogger.info(s"core3.http.controllers.auth0.ServiceController::PublicAction > Access to [${request.method}] @ [${request.uri}] " +
              s"allowed for client [unknown] @ [${request.remoteAddress}].")
        }

        okHandler(request, clientID)
    }
  }
}
