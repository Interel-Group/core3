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

//enables implicit conversions
import scala.language.implicitConversions

class ContainerDefinitions[T <: ContainerDefinition](private val definitions: Map[ContainerType, T]) {
  def apply(objectType: ContainerType): T =
    definitions.get(objectType) match {
      case Some(definition) => definition
      case None => throw new IllegalArgumentException(s"core3.database.containers.ContainerDefinitions.apply > No definition found for containers of type [$objectType].")
    }

  def supportedContainers: Vector[ContainerType] = definitions.keys.toVector
}

object ContainerDefinitions {
  implicit def mapToClass[T <: ContainerDefinition](definitions: Map[ContainerType, T]): ContainerDefinitions[T] = new ContainerDefinitions[T](definitions)
}
