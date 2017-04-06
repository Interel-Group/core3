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

import core3.database.containers.Container
import core3.database.{ContainerType, ContainerViewType, ObjectID}
import play.api.libs.json.JsValue

import scala.collection.mutable

/**
  * Container view trait for grouping related containers.
  */
trait ContainerView {
  /**
    * Retrieves the core object's ID.
    *
    * @return the requested ID
    */
  def coreObjectID: ObjectID

  /**
    * Retrieves the core object's type.
    *
    * @return the requested type
    */
  def coreObjectType: ContainerType

  /**
    * Retrieves the view's type.
    *
    * @return the requested type
    */
  def viewType: ContainerViewType

  /**
    * Checks if the supplied container is associated with the view.
    *
    * @param container the container to be checked
    * @return true, if the container is associated
    */
  def isAssociatedWith(container: Container): Boolean

  /**
    * Query configuration data for retrieving associated containers.
    */
  val fieldQueries: Map[String, ContainerQueryData]

  /**
    * Actual field data currently stored in the view.
    */
  protected val fieldData: mutable.HashMap[String, Vector[Container]]

  /**
    * Load the supplied containers in the specified field.
    *
    * @param field the affected field
    * @param data  the data to be loaded
    */
  def loadFieldData(field: String, data: Vector[Container]): Unit = {
    if (fieldData.contains(field)) {
      fieldData.put(field, data.map(c => c))
    }
  }

  /**
    * Loads the supplied map of fields to associated containers into the view.
    *
    * @param data the data to be loaded
    */
  def loadFieldsData(data: Map[String, Vector[Container]]): Unit = {
    data.foreach {
      case (field, currentData) => loadFieldData(field, currentData)
    }
  }
}

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
