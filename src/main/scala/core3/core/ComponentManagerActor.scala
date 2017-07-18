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

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.config.ConfigRenderOptions
import core3.config.{DynamicConfig, RuntimeConfig, StaticConfig}
import core3.core.Component.{ActionResult, ExecuteAction}
import core3.utils.Time.getCurrentTimestamp
import core3.utils.Timestamp
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * System and component manager.
  *
  * The state of the system/app can be altered by setting System Modes ([[core3.core.ComponentManagerActor.SetMode]]) and/or
  * by sending action to components ([[core3.core.ComponentManagerActor.ExecuteComponentAction]]).
  *
  * @param components the components in use by the system
  */
class ComponentManagerActor(private val components: Map[String, ActorRef])(implicit ec: ExecutionContext, timeout: Timeout) extends Actor {

  import ComponentManagerActor._

  private val modes = Map[SystemMode, ModeState](
    SystemMode.Maintenance -> ModeState(state = false),
    SystemMode.Trace -> ModeState(state = false),
    SystemMode.Metrics -> ModeState(state = false)
  )

  private def handle_ExecuteComponentAction(
    component: String, action: String, params: Option[Map[String, Option[String]]]
  ): Future[ActionResult] = {
    if (components.contains(component)) {
      (components(component) ? ExecuteAction(action, params)).mapTo[ActionResult]
    } else {
      Future.failed(new IllegalStateException(s"core3.core.ComponentManagerActor::handle_ExecuteComponentAction > Component [$component] does not exist."))
    }
  }

  private def handle_GetComponents(): Future[ActionResult] = {
    Future.successful(ActionResult(wasSuccessful = true, data = Some(Json.toJson(components.keys))))
  }

  private def handle_GetStaticConfig(): Future[ActionResult] = {
    val data = Json.parse(StaticConfig.get.root().render(ConfigRenderOptions.concise()))
    Future.successful(ActionResult(wasSuccessful = true, data = Some(data)))
  }

  private def handle_GetDynamicConfig(): Future[ActionResult] = {
    val data = Json.parse(DynamicConfig.get.root().render(ConfigRenderOptions.concise()))
    Future.successful(ActionResult(wasSuccessful = true, data = Some(data)))
  }

  private def handle_ReloadDynamicConfig(): Future[ActionResult] = {
    try {
      DynamicConfig.reload()
      Future.successful(ActionResult(wasSuccessful = true))
    } catch {
      case NonFatal(e) => Future.successful(ActionResult(wasSuccessful = false, message = Some(e.getMessage)))
    }
  }

  private def handle_GetModes(): Future[ActionResult] = {
    val data =
      Json.toJson(
        modes.map {
          case (k, v) =>
            k.toString -> Json.obj("state" -> v.state, "lastUpdated" -> v.lastUpdated)
        }
      )

    Future.successful(ActionResult(wasSuccessful = true, message = None, data = Some(data)))
  }

  private def handle_GetMode(mode: SystemMode): Future[Boolean] = {
    if (modes.contains(mode)) {
      Future.successful(modes(mode).state)
    } else {
      Future.failed(new IllegalStateException(s"core3.core.ComponentManagerActor::handle_GetMode > Mode [$mode] is not supported."))
    }
  }

  private def handle_SetMode(mode: SystemMode, enabled: Boolean): Future[ActionResult] = {
    if (modes.contains(mode)) {
      modes(mode).state = enabled
      modes(mode).lastUpdated = getCurrentTimestamp

      mode match {
        case SystemMode.Maintenance => RuntimeConfig.maintenance = enabled
        case SystemMode.Trace => RuntimeConfig.trace = enabled
        case SystemMode.Metrics => RuntimeConfig.metrics = enabled
      }

      Future.successful(ActionResult(wasSuccessful = true))
    } else {
      Future.failed(new IllegalStateException(s"core3.core.ComponentManagerActor::handle_SetMode > Mode [$mode] is not supported."))
    }
  }

  override def receive: Receive = {
    case ExecuteComponentAction(component, action, params) => handle_ExecuteComponentAction(component, action, params) pipeTo sender
    case GetComponents() => handle_GetComponents() pipeTo sender
    case GetStaticConfig() => handle_GetStaticConfig() pipeTo sender
    case GetDynamicConfig() => handle_GetDynamicConfig() pipeTo sender
    case ReloadDynamicConfig() => handle_ReloadDynamicConfig() pipeTo sender
    case GetModes() => handle_GetModes() pipeTo sender
    case GetMode(mode) => handle_GetMode(mode) pipeTo sender
    case SetMode(mode, enabled) => handle_SetMode(mode, enabled) pipeTo sender
  }
}

object ComponentManagerActor {

  sealed trait SystemMode

  object SystemMode {

    case object Maintenance extends SystemMode

    case object Trace extends SystemMode

    case object Metrics extends SystemMode

  }

  /**
    * Message for executing component actions.
    *
    * @param component the component to execute the specified action
    * @param action    the action to be executed
    * @param params    action parameters (if any)
    * @return Future[ [[core3.core.Component.ActionResult]] ] - the result of the action
    */
  case class ExecuteComponentAction(component: String, action: String, params: Option[Map[String, Option[String]]] = None)

  /**
    * Message for retrieving all registered components.
    *
    * @return Future[ [[core3.core.Component.ActionResult]] ] - the list of components as a JSON value
    */
  case class GetComponents()

  /**
    * Message for retrieving the current static configuration.
    *
    * @return Future[ [[core3.core.Component.ActionResult]] ] - the current static config as a JSON value
    */
  case class GetStaticConfig()

  /**
    * Message for retrieving the current dynamic configuration.
    *
    * @return Future[ [[core3.core.Component.ActionResult]] ] - the current dynamic config as a JSON value
    */
  case class GetDynamicConfig()

  /**
    * Message for reloading the current dynamic configuration.
    *
    * Note: See [[core3.config.DynamicConfig.reload]] for more information.
    *
    * @return Future[ [[core3.core.Component.ActionResult]] ] - nothing; not data/message is set
    */
  case class ReloadDynamicConfig()

  /**
    * Message for retrieving the current system modes.
    *
    * @return Future[ [[core3.core.Component.ActionResult]] ] - the current system modes as a JSON value
    */
  case class GetModes()

  /**
    * Message for retrieving the current state of the specified system mode.
    *
    * Note: For internal system use only.
    *
    * @param mode the mode to be checked
    * @return Future[Boolean] - true, if the specified mode is enabled
    */
  case class GetMode(mode: SystemMode)

  /**
    * Message for setting the state of the specified system mode.
    *
    * @param mode    the mode to be updated
    * @param enabled set to true, if the mode is to be enabled
    * @return Future[ [[core3.core.Component.ActionResult]] ] - nothing; no data/message is set
    */
  case class SetMode(mode: SystemMode, enabled: Boolean)

  private case class ModeState(var lastUpdated: Timestamp = getCurrentTimestamp, var state: Boolean)

  def props(
    components: Map[String, ActorRef]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[ComponentManagerActor], components, ec, timeout
  )
}