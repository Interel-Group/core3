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

import play.api.libs.json._

/**
  * Container class for workflow requests.
  *
  * @param workflowName the requested workflow name
  * @param rawParams    the parameters for the workflow
  */
case class WorkflowRequest(workflowName: String, rawParams: JsValue, returnOutputData: Boolean = false) {
  def asJson: JsValue = Json.toJson(this)
}

object WorkflowRequest {
  def apply(jsonString: String): WorkflowRequest = Json.parse(jsonString).as[WorkflowRequest]

  def apply(json: JsValue): WorkflowRequest = json.as[WorkflowRequest]

  implicit val writesWorkflowRequest: Writes[WorkflowRequest] = Writes[WorkflowRequest] {
    obj =>
      Json.obj(
        "workflowName" -> obj.workflowName,
        "rawParams" -> obj.rawParams,
        "returnOutputData" -> obj.returnOutputData
      )
  }

  implicit val readsWorkflowRequest: Reads[WorkflowRequest] = Reads[WorkflowRequest] {
    json =>
      JsSuccess(
        WorkflowRequest(
          (json \ "workflowName").as[String],
          (json \ "rawParams").as[JsValue],
          (json \ "returnOutputData").as[Boolean]
        )
      )
  }
}