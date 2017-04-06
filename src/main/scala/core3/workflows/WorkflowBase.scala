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
package core3.workflows

import core3.security.UserTokenBase
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Base class for defining workflows.
  */
trait WorkflowBase {
  /**
    * Retrieves the unique name of the workflow.
    *
    * @return the workflow name
    */
  def name: String

  /**
    * Specifies whether the workflow is read-only or it makes changes.
    *
    * A workflow specified as read-only will not be allowed to commit data.
    *
    * @return true, if the workflow makes no data changes
    */
  def readOnly: Boolean

  /**
    * Specifies whether the parameters contain sensitive information that should not be stored in the transaction logs.
    *
    * @return true, if the parameters contain sensitive info
    */
  def withSensitiveParams: Boolean = false

  /**
    * Specifies whether the data contains sensitive information that should not be stored in the transaction logs.
    *
    * @return true, if the data contains sensitive info
    */
  def withSensitiveData: Boolean = false

  /**
    * Verifies and converts the supplied raw JSON parameters into a format that can be used by the workflow.
    *
    * @param rawParams the raw parameters to parse
    * @return the parsed parameters
    */
  def parseParameters(rawParams: JsValue)(implicit ec: ExecutionContext): Future[WorkflowParameters]

  /**
    * Loads all data that will be needed for the workflow to execute its action.
    *
    * @param params        the parameters to use
    * @param queryHandlers a container holding all relevant query handlers (as supplied by a [[core3.workflows.WorkflowEngineComponent]]
    * @return the collected data
    */
  def loadData(params: WorkflowParameters, queryHandlers: DataQueryHandlers)(implicit ec: ExecutionContext): Future[InputData]

  /**
    * Executes the workflow's action with the supplied parameters and data.
    *
    * @param requestID the ID of the workflow request
    * @param user      the user that made the request
    * @param params    the parameters to use
    * @param data      the data to work with
    * @return a tuple containing the result of the operation and any updated data to be stored by the engine
    */
  def executeAction(requestID: RequestID, user: UserTokenBase, params: WorkflowParameters, data: InputData)(implicit ec: ExecutionContext): Future[(WorkflowResult, OutputData)]
}
