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

import core3.database.containers.{ContainerDefinitions, JsonContainerDefinition}
import core3.http.responses.ServiceResponse
import play.api.libs.json._

/**
  * Container class for workflow results.
  *
  * @param wasSuccessful set to true, if the workflow completed successfully
  * @param message       a message describing the result (optional)
  * @param data          data associated with the result (optional)
  * @param requestID     the ID associated with the workflow request
  */
case class WorkflowResult(wasSuccessful: Boolean, requestID: RequestID, message: Option[String] = None, data: Option[JsValue] = None) extends ServiceResponse {
  override def asJson: JsValue = Json.toJson(this)

  def withData(data: OutputData)(implicit definitions: ContainerDefinitions[JsonContainerDefinition]): WorkflowResult = {
    this.copy(
      data = Some(
        Json.obj(
          "add" -> data.add.map(container => definitions(container.objectType).toJsonData(container)),
          "update" -> data.update.map(container => definitions(container.objectType).toJsonData(container))
        )
      )
    )
  }

  override def combineWith(other: ServiceResponse): ServiceResponse = {
    if (this eq other) {
      throw new IllegalArgumentException(s"core3.workflows.WorkflowResult::combineWith > Cannot combine with self.")
    }

    WorkflowResult(
      this.wasSuccessful && other.wasSuccessful,
      this.requestID,
      this.message.map {
        thisMessage =>
          other.message.map {
            otherMessage => s"[$thisMessage] with [$otherMessage]."
          }.getOrElse(thisMessage)
      }.orElse(other.message),

      this.data.map {
        thisData =>
          other.data.map {
            otherData =>
              (thisData, otherData) match {
                case (thisObject: JsObject, otherObject: JsObject) => thisObject ++ otherObject
                case _ => Json.obj("this" -> thisData, "other" -> otherData)
              }
          }.getOrElse(thisData)
      }.orElse(other.data)
    )
  }
}

object WorkflowResult {
  implicit val writesWorkflowResult: Writes[WorkflowResult] = Json.writes[WorkflowResult]
  implicit val readsWorkflowResult: Reads[WorkflowResult] = Json.reads[WorkflowResult]
}
