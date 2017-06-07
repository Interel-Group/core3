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
package core3.database.views

import play.api.libs.json.JsValue

/**
  * Implementation & usage trait for container views supporting JSON data handling.
  */
trait JSONContainerViewCompanion {
  /**
    * Converts the supplied view to a JSON value.
    *
    * @param container the view to be converted
    * @return the view as a JSON value
    */
  def toJsonData(container: ContainerView): JsValue

  /**
    * Converts the supplied JSON value to a view.
    *
    * @param data the JSON value to be converted
    * @return the converted view
    */
  def fromJsonData(data: JsValue): ContainerView
}
