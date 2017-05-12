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
package core3.database.dals.memory

import akka.actor.Props
import akka.util.Timeout
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers.{Container, MutableContainer}
import core3.database.dals.{DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import play.api.libs.json.Json

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * A Database Abstraction Layer that directly implements a simple in-memory database.
  *
  * Warning: The database offers NO persistence and all data is lost as soon the instance is destroyed.
  *
  * @param supportedContainers a list of supported containers
  */
class MemoryOnlyDB(private val supportedContainers: Vector[String])
  (implicit ec: ExecutionContext, timeout: Timeout) extends DatabaseAbstractionLayerComponent {
  private val instanceID = java.util.UUID.randomUUID()
  private val store = new mutable.HashMap[ContainerType, mutable.HashMap[ObjectID, Container]]

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private val count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  override protected def handle_GetDatabaseIdentifier: String = s"core3.database.dals.memory.MemoryOnlyDB[$instanceID]"

  override protected def handle_GetLayerType: LayerType = LayerType.MemoryOnlyDB

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = supportedContainers

  override def shutdown(): Unit = {
    store.clear()
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (store.contains(objectsType)) {
      store -= objectsType
      Future.successful(true)
    } else {
      Future.failed(
        new IllegalStateException(s"core3.database.dals.memory.MemoryOnlyDB::handle_ClearDatabaseStructure > " +
          s"[$instanceID] Store [$objectsType] does not exist.")
      )
    }
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (!store.contains(objectsType)) {
      store += (objectsType -> new mutable.HashMap[ObjectID, Container])
      Future.successful(true)
    } else {
      Future.failed(
        new IllegalStateException(s"core3.database.dals.memory.MemoryOnlyDB::handle_BuildDatabaseStructure > " +
          s"[$instanceID] Store [$objectsType] already exists.")
      )
    }
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    Future.successful(store.contains(objectsType))
  }

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future.successful(
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "layerType" -> handle_GetLayerType.toString,
                "id" -> handle_GetDatabaseIdentifier,
                "counters" -> Json.obj(
                  "executeAction" -> count_ExecuteAction,
                  "genericQuery" -> count_GenericQuery,
                  "customQuery" -> count_CustomQuery,
                  "get" -> count_Get,
                  "create" -> count_Create,
                  "update" -> count_Update,
                  "delete" -> count_Delete
                )
              )
            )
          )
      }
    )
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    count_GenericQuery += 1

    try {
      Future.successful(store(objectsType).values.toVector)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    Future.failed[Vector[Container]](new UnsupportedOperationException(s"core3.database.dals.memory.MemoryOnlyDB::handle_GetCustomQueryResult > [$instanceID] Custom queries are not supported."))
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    count_Get += 1
    Future.successful(store(objectType)(objectID))
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    count_Create += 1
    store(container.objectType) += (container.id -> container)
    Future.successful(true)
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    count_Update += 1
    store(container.objectType) += (container.id -> container)
    Future.successful(true)
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    count_Delete += 1
    store(objectType) -= objectID
    Future.successful(true)
  }
}

object MemoryOnlyDB extends ComponentCompanion {
  def props(supportedContainers: Vector[String])(implicit ec: ExecutionContext, timeout: Timeout): Props =
    Props(classOf[MemoryOnlyDB], supportedContainers, ec, timeout)

  override def getActionDescriptors: Seq[ActionDescriptor] = {
    Seq(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
