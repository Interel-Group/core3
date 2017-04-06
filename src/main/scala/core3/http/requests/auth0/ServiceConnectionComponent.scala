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
package core3.http.requests.auth0

import akka.actor.Props
import com.typesafe.config.Config
import core3.core.Component.ActionDescriptor
import core3.core.ComponentCompanion
import core3.http.requests.ServiceConnectionComponentBase
import core3.security.{Auth0UserToken, UserTokenBase}
import pdi.jwt.{JwtJson, JwtOptions}
import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Connection handler for secure services, based on [[core3.http.controllers.auth0.ServiceController]].
  * <br><br>
  * Note: Uses JSON for data exchange.
  *
  * @param ws            the web service client to use for making requests
  * @param serviceConfig the target service configuration
  * @param authConfig    the auth configuration
  */
class ServiceConnectionComponent(
  ws: WSClient,
  serviceConfig: Config,
  authConfig: Config
)(implicit ec: ExecutionContext) extends ServiceConnectionComponentBase(serviceConfig.getString("identifier")) {
  private val domain = authConfig.getString("domain")
  private val clientID = authConfig.getString("clientId")
  private val clientSecret = authConfig.getString("clientSecret")
  private val serviceID = serviceConfig.getString("id")
  private val clientAccessTokenRenewalTimeBeforeExpiration: Long = 5000
  //in ms
  private var clientAccessToken: Option[JsValue] = None
  private var rawClientAccessToken: Option[String] = None
  private val auditLogger = Logger("audit")

  override protected def shutdown(): Unit = {}

  /**
    * Retrieves a client access token from the auth provider.
    *
    * @return the requested token: (decoded token, encoded token)
    */
  private def getClientAccessToken: Future[(JsValue, String)] = {
    try {
      if (clientAccessToken.isEmpty || (((clientAccessToken.get \ "exp").as[Long] * 1000) + clientAccessTokenRenewalTimeBeforeExpiration) < System.currentTimeMillis()) {
        val tokenResponse = ws.url(s"https://$domain/oauth/token")
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
          .post(
            Json.obj(
              "audience" -> serviceURI,
              "client_id" -> clientID,
              "client_secret" -> clientSecret,
              "grant_type" -> "client_credentials"
            )
          )

        tokenResponse.flatMap {
          response =>
            (for {
              encodedToken <- (response.json \ "access_token").asOpt[String]
              token <- JwtJson.decodeJson(encodedToken, JwtOptions(signature = false)).toOption
              audience <- (token \ "aud").asOpt[String]
            } yield {
              if (audience == serviceURI) {
                auditLogger.info(s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Successfully retrieved new client access token for client [$clientID] and service [$serviceURI].")
                clientAccessToken = Some(token)
                rawClientAccessToken = Some(encodedToken)
                Future.successful(token, encodedToken)
              } else {
                val message = s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Invalid audience found in token; expected [$serviceURI] found [$audience]."
                auditLogger.error(message)
                Future.failed(new RuntimeException(message))
              }
            }).getOrElse {
              val message = s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Client access token not sent by provider for service [$serviceID @ $serviceURI]."
              auditLogger.error(message)
              Future.failed(new RuntimeException(message))
            }
        }.recoverWith {
          case NonFatal(e) =>
            val message = s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Client access token retrieval for service [$serviceURI] failed with message [${e.getMessage}]."
            auditLogger.error(message)
            e.printStackTrace()
            Future.failed(new RuntimeException(message))
        }
      } else {
        auditLogger.info(s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Successfully retrieved existing client access token for client [$clientID] and service [$serviceURI].")
        Future.successful(clientAccessToken.get, rawClientAccessToken.get)
      }
    } catch {
      case NonFatal(e) =>
        val message = s"core3.http.requests.auth0.ServiceConnectionComponent::getClientAccessToken > Exception encountered while retrieving client access token for service [$serviceURI]."
        auditLogger.error(message)
        e.printStackTrace()
        Future.failed(new RuntimeException(message))
    }
  }

  override protected def call(user: Option[UserTokenBase], method: String, data: JsObject): Future[(Int, JsValue)] = {
    getClientAccessToken.flatMap {
      case (_, rawToken) =>
        val serviceRequest = ws.url(serviceURI)
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Bearer $rawToken",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> user.map {
              case auth0Token: Auth0UserToken => auth0Token.delegationIdToken
              case token => throw new IllegalArgumentException(s"Unexpected token type supplied: [${token.getClass.getName}]")
            }.getOrElse("None"),
            core3.http.HeaderNames.USER_ACCESS_TOKEN -> user.map {
              case auth0Token: Auth0UserToken => auth0Token.accessToken
              case token => throw new IllegalArgumentException(s"Unexpected token type supplied: [${token.getClass.getName}]")
            }.getOrElse("None")
          )
          .withMethod(method)

        val serviceResponse = (method.toLowerCase match {
          case "get" => serviceRequest.withQueryString(data.fields.map { case (field, value) => (field, value.toString) }: _*)
          case "delete" => serviceRequest.withQueryString(data.fields.map { case (field, value) => (field, value.toString) }: _*)
          case _ => serviceRequest.withBody(data)
        }).execute()

        serviceResponse.flatMap {
          response =>
            auditLogger.info(s"core3.http.requests.auth0.ServiceConnectionComponent::call > Call to service [$method @ $serviceURI] completed with status [${response.status} / ${response.statusText}].")
            Future.successful((response.status, response.json))
        }
    }.recoverWith {
      case NonFatal(e) =>
        val message = s"core3.http.requests.auth0.ServiceConnectionComponent::call > Call to service [$method @ $serviceURI] failed with message [${e.getMessage}]."
        auditLogger.error(message)
        e.printStackTrace()
        Future.failed[(Int, JsValue)](new RuntimeException(message))
    }
  }
}

object ServiceConnectionComponent extends ComponentCompanion {
  def props(ws: WSClient, serviceConfig: Config, authConfig: Config)(implicit ec: ExecutionContext): Props =
    Props(classOf[ServiceConnectionComponent], ws, serviceConfig, authConfig, ec)

  override def getActionDescriptors: Seq[ActionDescriptor] = {
    Seq(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
