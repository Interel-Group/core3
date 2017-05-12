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
package core3.database.containers

import core3.database.ContainerType
import play.api.libs.json.JsValue

object JSONConverter {
  private var jsonCompanions: Option[Map[ContainerType, JSONContainerCompanion]] = None

  def isInitialized: Boolean = jsonCompanions.isDefined

  def initialize(jsonContainerCompanions: Map[ContainerType, JSONContainerCompanion]): Unit = {
    if (jsonCompanions.isEmpty) {
      jsonCompanions = Some(jsonContainerCompanions)
    } else {
      throw new IllegalStateException(s"core3.database.containers.JSONConverter::initialize > Cannot initialize converter more than once.")
    }
  }

  def toJsonData(container: Container): JsValue = {
    jsonCompanions match {
      case Some(companions) =>
        if (companions.contains(container.objectType)) {
          companions(container.objectType).toJsonData(container)
        } else {
          throw new IllegalArgumentException(s"core3.database.containers.JSONConverter::toJsonData > Container type [${container.objectType}] not found.")
        }

      case None =>
        throw new IllegalStateException("core3.database.containers.JSONConverter::toJsonData() > Converter not initialized.")
    }
  }

  def fromJsonData(objectType: ContainerType, data: JsValue): Container = {
    jsonCompanions match {
      case Some(companions) =>
        if (companions.contains(objectType)) {
          companions(objectType).fromJsonData(data)
        } else {
          throw new IllegalArgumentException(s"core3.database.containers.JSONConverter::fromJsonData > Container type [$objectType] not found.")
        }

      case None =>
        throw new IllegalStateException("core3.database.containers.JSONConverter::fromJsonData() > Converter not initialized.")
    }
  }
}
