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
package core3.core.cli

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import core3.core.Component.ActionResult
import core3.core.ComponentManagerActor._

import scala.concurrent.Future

/**
  * Simple component command router.
  */
object CommandRouter {
  /**
    * Sends a command to the supplied manager based on the specified parameters.
    *
    * @param manager   the manager to route the command/action to
    * @param component the component responsible for processing the action
    * @param action    the action to be executed
    * @param params    any parameters that may be needed
    * @return the result of the action
    */
  def route(
    manager: ActorRef,
    component: String,
    action: String,
    params: Option[Map[String, Option[String]]]
  )(implicit timeout: Timeout): Future[ActionResult] = {
    val future = component match {
      case "system" => action match {
        case "get_modes" => manager ? GetModes()
        case "get_components" => manager ? GetComponents()
        case "get_static_config" => manager ? GetStaticConfig()
        case "get_dynamic_config" => manager ? GetDynamicConfig()
        case "reload_dynamic_config" => manager ? ReloadDynamicConfig()
        case "maintenance_enable" => manager ? SetMode(SystemMode.Maintenance, enabled = true)
        case "maintenance_disable" => manager ? SetMode(SystemMode.Maintenance, enabled = false)
        case "trace_enable" => manager ? SetMode(SystemMode.Trace, enabled = true)
        case "trace_disable" => manager ? SetMode(SystemMode.Trace, enabled = false)
        case "metrics_enable" => manager ? SetMode(SystemMode.Metrics, enabled = true)
        case "metrics_disable" => manager ? SetMode(SystemMode.Metrics, enabled = false)
      }

      case _ => manager ? ExecuteComponentAction(component, action, params)
    }

    future.mapTo[ActionResult]
  }

  /**
    *
    * Sends a command to the supplied manager based on the specified parameters.
    *
    * @param manager the manager to route the command/action to
    * @param command the command to be sent
    * @return the result of the action
    */
  def route(manager: ActorRef, command: CommandConfig)(implicit timeout: Timeout): Future[ActionResult] = {
    (command.component, command.action) match {
      case (Some(component), Some(action)) => route(manager, component, action, command.params)
      case _ => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Parameters [component] and/or [action] are missing.")))
    }
  }
}
