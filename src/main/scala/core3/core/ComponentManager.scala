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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import core3.core.Component.ActionResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * A wrapper class for sending requests to [[core3.core.ComponentManagerActor]].
  *
  * @param actor the actor to be used for all requests
  */
class ComponentManager(private val actor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {

  import ComponentManagerActor._

  /**
    * Retrieves the underlying [[akka.actor.ActorRef]].
    *
    * @return the actor ref
    */
  def getRef: ActorRef = actor

  /**
    * Message for executing component actions.
    *
    * @param component the component to execute the specified action
    * @param action    the action to be executed
    * @param params    action parameters (if any)
    * @return the result of the action
    */
  def executeComponentAction(component: String, action: String, params: Option[Map[String, Option[String]]] = None): Future[ActionResult] = {
    (actor ? ExecuteComponentAction(component, action, params)).mapTo[ActionResult]
  }

  /**
    * Message for retrieving the list of configured components.
    *
    * @return the configured components
    */
  def getComponents: Future[ActionResult] = {
    (actor ? GetComponents()).mapTo[ActionResult]
  }

  /**
    * Message for retrieving the current static configuration.
    *
    * @return the current static config as a JSON value
    */
  def getStaticConfig: Future[ActionResult] = {
    (actor ? GetStaticConfig()).mapTo[ActionResult]
  }

  /**
    * Message for retrieving the current dynamic configuration.
    *
    * @return the current dynamic config as a JSON value
    */
  def getDynamicConfig: Future[ActionResult] = {
    (actor ? GetDynamicConfig()).mapTo[ActionResult]
  }

  /**
    * Message for reloading the current dynamic configuration.
    *
    * Note: See [[core3.config.DynamicConfig.reload]] for more information.
    *
    * @return nothing; not data/message is set
    */
  def reloadDynamicConfig(): Future[ActionResult] = {
    (actor ? ReloadDynamicConfig()).mapTo[ActionResult]
  }

  /**
    * Message for retrieving the current system modes.
    *
    * @return the current state of the specified system mode
    */
  def getMode(mode: SystemMode): Future[Boolean] = {
    (actor ? GetMode(mode)).mapTo[Boolean]
  }

  /**
    * Message for retrieving the current system modes.
    *
    * @return the current system modes as a JSON value
    */
  def getModes: Future[ActionResult] = {
    (actor ? GetModes()).mapTo[ActionResult]
  }

  /**
    * Message for setting the state of the specified system mode.
    *
    * @param mode    the mode to be updated
    * @param enabled set to true, if the mode is to be enabled
    * @return nothing; no data/message is set
    */
  def setMode(mode: SystemMode, enabled: Boolean): Future[ActionResult] = {
    (actor ? SetMode(mode, enabled)).mapTo[ActionResult]
  }
}
