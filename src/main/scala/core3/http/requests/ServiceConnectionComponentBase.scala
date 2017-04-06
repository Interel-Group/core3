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
package core3.http.requests

import akka.pattern.pipe
import core3.core.Component
import core3.core.Component.ActionResult
import core3.security.UserTokenBase
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

abstract class ServiceConnectionComponentBase(protected val serviceURI: String)(implicit ec: ExecutionContext) extends Component {
  //stats
  private var count_ExecuteAction: Long = 0
  private var count_Get: Long = 0
  private var count_Post: Long = 0
  private var count_Put: Long = 0
  private var count_Delete: Long = 0
  private var count_Patch: Long = 0

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future {
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "serviceURI" -> serviceURI,
                "counters" -> Json.obj(
                  "executeAction" -> count_ExecuteAction,
                  "get" -> count_Get,
                  "post" -> count_Post,
                  "put" -> count_Put,
                  "delete" -> count_Delete,
                  "patch" -> count_Patch
                )
              )
            )
          )
      }
    }
  }

  /**
    * Performs a service call using the supplied parameters.
    *
    * @param user   the user token to send to the service (if any)
    * @param method the request method to use
    * @param data   the data to send
    * @return the result of the call: (response code, response data)
    */
  protected def call(user: Option[UserTokenBase], method: String, data: JsObject): Future[(Int, JsValue)]

  import ServiceConnectionComponentBase._

  addReceiver {
    case Get(user, data) =>
      count_Get += 1
      call(user, "GET", data) pipeTo sender

    case Post(user, data) =>
      count_Post += 1
      call(user, "POST", data) pipeTo sender

    case Put(user, data) =>
      count_Put += 1
      call(user, "PUT", data) pipeTo sender

    case Delete(user, data) =>
      count_Delete += 1
      call(user, "DELETE", data) pipeTo sender

    case Patch(user, data) =>
      count_Patch += 1
      call(user, "PATCH", data) pipeTo sender
  }
}

object ServiceConnectionComponentBase {

  /**
    * Sends a GET request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return Future[(Int, JsValue)] - the result of the request: (response code, response data)
    */
  case class Get(user: Option[UserTokenBase], data: JsObject)

  /**
    * Sends a POST request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return Future[(Int, JsValue)] - the result of the request: (response code, response data)
    */
  case class Post(user: Option[UserTokenBase], data: JsObject)

  /**
    * Sends a PUT request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return Future[(Int, JsValue)] - the result of the request: (response code, response data)
    */
  case class Put(user: Option[UserTokenBase], data: JsObject)

  /**
    * Sends a DELETE request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return Future[(Int, JsValue)] - the result of the request: (response code, response data)
    */
  case class Delete(user: Option[UserTokenBase], data: JsObject)

  /**
    * Sends a PATCH request to the service.
    *
    * @param user the user token to send to the service (if any)
    * @param data the data to send
    * @return Future[(Int, JsValue)] - the result of the request: (response code, response data)
    */
  case class Patch(user: Option[UserTokenBase], data: JsObject)

}
