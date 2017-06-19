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
package core3.database.views.core

import core3.database.containers.Container
import core3.database.views.{ContainerQueryData, ContainerView, JSONContainerViewCompanion}
import core3.database.{ContainerType, ContainerViewType, ObjectID, containers}
import play.api.libs.json._

import scala.collection.mutable

case class Group(private val core: containers.core.Group) extends ContainerView {
  def this(group: containers.core.Group, items: Vector[Container]) = {
    this(group)
    fieldData("items") = items
  }

  override def coreObjectID: ObjectID = core.id

  override def coreObjectType: ContainerType = core.objectType

  override def viewType: ContainerViewType = "Group"

  //available fields
  def group: containers.core.Group = core

  def items: Vector[Container] = fieldData("items")

  override protected val fieldData: mutable.HashMap[String, Vector[Container]] = mutable.HashMap(
    "items" -> Vector.empty[Container]
  )

  override val fieldQueries = Map(
    "items" -> ContainerQueryData(
      containerType = core.itemsType,
      withFilter = {
        container =>
          core.items.contains(container.id)
      }
    )
  )

  override def isAssociatedWith(container: Container): Boolean = {
    ((core.objectType == container.objectType && coreObjectID == container.id)
      || (container.objectType == core.itemsType && core.items.contains(container.id)))
  }
}

object Group extends JSONContainerViewCompanion {
  def apply(core: containers.core.Group, items: Vector[Container]) = new Group(core, items)

  private val writes = Json.writes[Group]

  private val reads = Json.reads[Group]

  def toJsonData(container: ContainerView): JsValue = Json.toJson(container.asInstanceOf[Group])(writes)

  def fromJsonData(data: JsValue): ContainerView = data.as[Group](reads)
}
