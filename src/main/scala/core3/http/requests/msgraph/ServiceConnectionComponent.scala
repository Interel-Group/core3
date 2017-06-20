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
package core3.http.requests.msgraph

import akka.actor.Props
import com.typesafe.config.Config
import core3.core.Component.ActionDescriptor
import core3.core.ComponentCompanion
import core3.http.requests.ServiceConnectionComponentBase
import core3.security.UserTokenBase
import pdi.jwt._
import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Connection handler for Microsoft Graph (https://graph.microsoft.com) services.
  * <br><br>
  * Note: Uses JSON for data exchange.
  *
  * @param ws            the web service client to use for making requests
  * @param serviceConfig the target service configuration
  */
class ServiceConnectionComponent(
  ws: WSClient,
  serviceConfig: Config
)(implicit ec: ExecutionContext) extends ServiceConnectionComponentBase(serviceConfig.getString("uri")) {
  private val authProvider = serviceConfig.getString("authProvider")
  private val tenantID = serviceConfig.getString("tenantId")
  private val clientID = serviceConfig.getString("clientId")
  private val clientSecret = serviceConfig.getString("clientSecret")
  private val scope = serviceConfig.getString("scope")

  private val authProviderURI = s"$authProvider/$tenantID/oauth2/v2.0/token"
  private val clientAccessTokenRenewalTimeBeforeExpiration: Long = 5000 //in ms
  private var clientAccessToken: Option[JsValue] = None
  private var rawClientAccessToken: Option[String] = None
  private val auditLogger = Logger("audit")

  /**
    * Retrieves a client access token from the auth provider.
    *
    * @return the requested token: (decoded token, encoded token)
    */
  private def getClientAccessToken: Future[(JsValue, String)] = {
    try {
      if (clientAccessToken.isEmpty || (((clientAccessToken.get \ "exp").as[Long] * 1000) + clientAccessTokenRenewalTimeBeforeExpiration) < System.currentTimeMillis()) {
        val tokenResponse = ws.url(authProviderURI)
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.FORM)
          .post(
            Map[String, Seq[String]](
              "client_id" -> Seq(clientID),
              "client_secret" -> Seq(clientSecret),
              "grant_type" -> Seq("client_credentials"),
              "scope" -> Seq(s"$serviceURI/$scope")
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
                auditLogger.info(s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Successfully retrieved new client access token for client [$clientID] and service [$serviceURI].")
                clientAccessToken = Some(token)
                rawClientAccessToken = Some(encodedToken)
                Future.successful(token, encodedToken)
              } else {
                val message = s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Invalid audience found in token; expected [$serviceURI] found [$audience]."
                auditLogger.error(message)
                Future.failed(new RuntimeException(message))
              }
            }).getOrElse {
              val errorMessage = s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Client access token not sent by provider [$authProviderURI] for service [$serviceURI]."
              val debugMessage = s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Token retrieval received response: [${response.body}]."
              auditLogger.error(errorMessage)
              auditLogger.debug(debugMessage)
              Future.failed(new RuntimeException(errorMessage))
            }
        }.recoverWith {
          case NonFatal(e) =>
            val message = s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Client access token retrieval for service [$serviceURI] from provider [$authProviderURI] failed with message [${e.getMessage}]."
            auditLogger.error(message)
            e.printStackTrace()
            Future.failed(new RuntimeException(message))
        }
      } else {
        auditLogger.info(s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Successfully retrieved existing client access token for client [$clientID] and service [$serviceURI].")
        Future.successful(clientAccessToken.get, rawClientAccessToken.get)
      }
    } catch {
      case NonFatal(e) =>
        val message = s"core3.http.requests.msgraph.ServiceConnectionComponent::getClientAccessToken > Exception encountered while retrieving client access token for service [$serviceURI] from provider [$authProviderURI]."
        auditLogger.error(message)
        e.printStackTrace()
        Future.failed(new RuntimeException(message))
    }
  }

  override protected def shutdown(): Unit = {}

  override protected def call(user: Option[UserTokenBase], method: String, data: JsObject): Future[(Int, JsValue)] = {
    val graphURI = (data \ "graphURI").as[String]
    val graphParams = (data \ "graphParams").asOpt[JsObject]

    getClientAccessToken.flatMap {
      case (_, rawToken) =>
        val serviceRequest = ws.url(s"$serviceURI/v1.0/$graphURI")
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Bearer $rawToken",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
          )
          .withMethod(method)

        val serviceResponse = graphParams match {
          case Some(params) =>
            (method.toLowerCase match {
              case "get" => serviceRequest.withQueryString(params.fields.map { case (field, value) => (field, value.toString) }: _*)
              case "delete" => serviceRequest.withQueryString(params.fields.map { case (field, value) => (field, value.toString) }: _*)
              case _ => serviceRequest.withBody(params)
            }).execute()
          case None => serviceRequest.execute()
        }

        serviceResponse.flatMap {
          response =>
            auditLogger.info(s"core3.http.requests.msgraph.ServiceConnectionComponent::call > Call to service [$method @ $serviceURI] completed with status [${response.status} / ${response.statusText}].")
            Future.successful((response.status, response.json))
        }
    }.recoverWith {
      case NonFatal(e) =>
        val message = s"core3.http.requests.msgraph.ServiceConnectionComponent::call > Call to service [$method @ $serviceURI] failed with message [${e.getMessage}]."
        auditLogger.error(message)
        e.printStackTrace()
        Future.failed[(Int, JsValue)](new RuntimeException(message))
    }
  }
}

object ServiceConnectionComponent extends ComponentCompanion {
  def props(ws: WSClient, serviceConfig: Config, authConfig: Config)(implicit ec: ExecutionContext): Props =
    Props(classOf[ServiceConnectionComponent], ws, serviceConfig, authConfig, ec)

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
