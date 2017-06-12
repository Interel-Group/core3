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
package core3.http.responses

import play.api.libs.json._

/**
  * Container class for generic service results results.
  *
  * @param wasSuccessful set to true, if the requested operation completed successfully
  * @param message       a message describing the result (optional)
  * @param data          data associated with the result (optional)
  */
case class GenericResult(wasSuccessful: Boolean, message: Option[String] = None, data: Option[JsValue] = None) extends ServiceResponse {
  override def asJson: JsValue = Json.toJson(this)

  override def combineWith(other: ServiceResponse): ServiceResponse = {
    if (this eq other) {
      throw new IllegalArgumentException(s"core3.http.responses.GenericResult::combineWith > Cannot combine with self.")
    }

    GenericResult(
      this.wasSuccessful && other.wasSuccessful,

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

object GenericResult {
  implicit val writesWorkflowResult: Writes[GenericResult] = Json.writes[GenericResult]
  implicit val readsWorkflowResult: Reads[GenericResult] = Json.reads[GenericResult]
}
