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
package core3.http.requests.local

import akka.actor.Props
import com.typesafe.config.Config
import core3.core.Component.ActionDescriptor
import core3.core.ComponentCompanion
import core3.http.requests.ServiceConnectionComponentBase
import core3.security.{LocalAuthUserToken, UserTokenBase}
import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Connection handler for secure services, based on [[core3.http.controllers.local.ServiceController]].
  * <br><br>
  * Note: Uses JSON for data exchange.
  *
  * @param ws         the web service client to use for making requests
  * @param authConfig the auth configuration
  */
class ServiceConnectionComponent(
  ws: WSClient,
  authConfig: Config
)(implicit ec: ExecutionContext) extends ServiceConnectionComponentBase(authConfig.getString("uri")) {
  private val serviceUser = authConfig.getString("serviceUser")
  private val serviceUserPassword = authConfig.getString("serviceUserPassword")
  private val auditLogger = Logger("audit")
  private var sessionID: Option[String] = None

  override protected def shutdown(): Unit = {}

  /**
    * Performs the requested service call and may attempt to re-authenticate with the provider.
    *
    * @param user                 the user to be sent with the request
    * @param method               the method to execute
    * @param data                 the data to be sent with the request
    * @param shouldReAuthenticate set to true, if the component should attempt to re-authenticate with the service
    * @return the result of the request
    */
  private def internal_call(user: Option[UserTokenBase], method: String, data: JsObject, shouldReAuthenticate: Boolean): Future[(Int, JsValue)] = {
    var serviceRequest = ws.url(serviceURI)
      .withHeaders(
        HeaderNames.ACCEPT -> MimeTypes.JSON,
        core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> sessionID.getOrElse("None"),
        core3.http.HeaderNames.USER_DELEGATION_TOKEN -> user.map {
          case localToken: LocalAuthUserToken => localToken.userID
          case token => throw new IllegalArgumentException(s"Unexpected token type supplied: [${token.getClass.getName}]")
        }.getOrElse("None")
      )
      .withMethod(method)

    if (sessionID.isEmpty || shouldReAuthenticate) {
      serviceRequest = serviceRequest.withAuth(serviceUser, serviceUserPassword, WSAuthScheme.BASIC)
    }

    val serviceResponse = (method.toLowerCase match {
      case "get" => serviceRequest.withQueryString(data.fields.map { case (field, value) => (field, value.toString) }: _*)
      case "delete" => serviceRequest.withQueryString(data.fields.map { case (field, value) => (field, value.toString) }: _*)
      case _ => serviceRequest.withBody(data)
    }).execute()

    serviceResponse.flatMap {
      response =>
        if (response.status == 401 && !shouldReAuthenticate) {
          internal_call(user, method, data, shouldReAuthenticate = true)
        } else {
          if (sessionID.isEmpty || shouldReAuthenticate) {
            response.header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN).foreach {
              session => sessionID = Some(session)
            }
          }

          auditLogger.info(s"core3.http.requests.local.LocalAuthServiceConnectionComponent::call > Call to service [$method @ $serviceURI] completed with status [${response.status} / ${response.statusText}].")
          Future.successful((response.status, response.json))
        }
    }
  }

  override protected def call(user: Option[UserTokenBase], method: String, data: JsObject): Future[(Int, JsValue)] = {
    internal_call(user, method, data, shouldReAuthenticate = false)
  }
}

object ServiceConnectionComponent extends ComponentCompanion {
  def props(ws: WSClient, authConfig: Config)(implicit ec: ExecutionContext): Props =
    Props(classOf[ServiceConnectionComponent], ws, authConfig, ec)

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}