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

import core3.database.{ContainerType, InvalidObjectID}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.immutable.Vector

/**
  * Stores a set of containers of the same type.
  *
  * @param objectsType the type of the containers
  * @param containers  the actual containers vector
  */
case class ContainerSet(objectsType: ContainerType, containers: Vector[Container])
  extends Container {
  override val objectType: ContainerType = "ContainerSet"

  override val id = InvalidObjectID
}

object ContainerSetJsonProtocol {
  implicit val containerSetPartialWrites = new Writes[ContainerSet] {
    def writes(obj: ContainerSet) = Json.obj(
      "objectsType" -> JsString(obj.objectsType),
      "setSize" -> JsNumber(obj.containers.size)
    )
  }

  implicit val containerSetPartialReads: Reads[ContainerSet] = (
    (__ \ "objectsType").read[String] and
      Reads(_ => JsSuccess(Vector[Container]()))
    ) (ContainerSet.apply _)

  implicit val containerSetFullWrites = new Writes[ContainerSet] {
    def writes(obj: ContainerSet) = Json.obj(
      "objectsType" -> JsString(obj.objectsType),
      "setSize" -> JsNumber(obj.containers.size),
      "containers" -> JsArray(obj.containers.map { c => JSONConverter.toJsonData(c, JsonDataFormat.Full) })
    )
  }

  implicit val containerSetFullReads: Reads[ContainerSet] = (
    (__ \ "objectsType").read[ContainerType] and __.json.pick
    ).tupled.flatMap {
    case (objectsType, js) =>
      val containers = js.as[List[JsValue]].map { c => JSONConverter.fromJsonData(objectsType, c) }.toVector
      Reads { _ => JsSuccess(ContainerSet(objectsType, containers)) }
  }
}
