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
package core3.core

import akka.actor.Actor
import akka.pattern.pipe
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface defining all necessary methods for component management.
  */
abstract class Component(implicit ec: ExecutionContext) extends Actor {

  import Component._

  protected def shutdown(): Unit

  protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult]

  private var receivers: Actor.Receive = {
    case ExecuteAction(action, params) => handle_ExecuteAction(action, params) pipeTo sender
  }

  protected def addReceiver(next: Actor.Receive): Unit = {
    receivers = receivers orElse next
  }

  override def postStop(): Unit = shutdown()

  override def receive: Receive = receivers
}

/**
  * A trait for component companion objects.
  */
trait ComponentCompanion {
  /**
    * Retrieves a component's action descriptors.
    *
    * @return the defined descriptors
    */
  def getActionDescriptors: Vector[Component.ActionDescriptor]
}

object Component {

  /**
    * Message for requesting for a component to execute an action.
    *
    * @param action the action to be executed
    * @param params the parameters for the action (if any); map of [param-name -> param-value]
    * @return Future[ [[core3.core.Component.ActionResult]] ] - action result
    */
  case class ExecuteAction(action: String, params: Option[Map[String, Option[String]]] = None)

  /**
    * Container for component action result data.
    *
    * @param wasSuccessful set to true, if the action was successful
    * @param message       the result, as a message (if any; depending on action)
    * @param data          the result, as data (if any; depending on action)
    */
  case class ActionResult(wasSuccessful: Boolean, message: Option[String] = None, data: Option[JsValue] = None)

  /**
    * Container for component action descriptors used for parsing CLI input and generating help messages.
    *
    * @param name        the name of the action
    * @param description the action description
    * @param arguments   action arguments
    */
  case class ActionDescriptor(name: String, description: String, arguments: Option[Map[String, String]])

  /**
    * Container for component info used for parsing CLI input and generating help messages.
    *
    * @param name        the name of the component
    * @param description the component description
    * @param companion   companion object
    */
  case class ComponentDescriptor(name: String, description: String, companion: ComponentCompanion)

  val writesActionResult: Writes[ActionResult] = Writes[ActionResult] {
    obj =>
      Json.obj(
        "wasSuccessful" -> obj.wasSuccessful,
        "message" -> obj.message,
        "data" -> obj.data
      )
  }

  val readsActionResult: Reads[ActionResult] = Reads[ActionResult] {
    json =>
      JsSuccess(
        ActionResult(
          (json \ "wasSuccessful").as[Boolean],
          (json \ "message").asOpt[String],
          (json \ "data").asOpt[JsValue]
        )
      )
  }
}
