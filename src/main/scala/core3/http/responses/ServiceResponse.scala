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

import play.api.libs.json.JsValue

/**
  * Base trait for service responses.
  */
trait ServiceResponse {
  def wasSuccessful: Boolean

  def message: Option[String]

  def data: Option[JsValue]

  def asJson: JsValue

  /**
    * Combines the current service response with the supplied one and returns a new one with all data and messages merged.
    *
    * Notes:
    * - If one of the service responses was not successful, the new service response should not be successful.
    * - All messages should be preserved, regardless of the success of the responses.
    * - All data should be preserved, regardless of the success of the responses.
    * - Should throw an exception if attempting to combine with itself.
    *
    * @param other the service response to combine this one with
    * @return the new, combined, service response
    */
  def combineWith(other: ServiceResponse): ServiceResponse
}
