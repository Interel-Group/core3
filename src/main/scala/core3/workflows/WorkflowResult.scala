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

import core3.database.containers.{JSONConverter, JsonDataFormat}
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

  def withData(data: OutputData): WorkflowResult = {
    this.copy(
      data = Some(
        Json.obj(
          "add" -> data.add.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full)),
          "update" -> data.update.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full))
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
  def apply(jsonString: String): WorkflowResult = Json.parse(jsonString).as[WorkflowResult]

  def apply(json: JsValue): WorkflowResult = json.as[WorkflowResult]

  implicit val writesWorkflowResult: Writes[WorkflowResult] = Writes[WorkflowResult] {
    obj =>
      Json.obj(
        "wasSuccessful" -> obj.wasSuccessful,
        "requestID" -> obj.requestID,
        "message" -> obj.message,
        "data" -> obj.data
      )
  }

  implicit val readsWorkflowResult: Reads[WorkflowResult] = Reads[WorkflowResult] {
    json =>
      JsSuccess(
        WorkflowResult(
          (json \ "wasSuccessful").as[Boolean],
          (json \ "requestID").as[RequestID],
          (json \ "message").asOpt[String],
          (json \ "data").asOpt[JsValue]
        )
      )
  }
}
