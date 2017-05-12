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
package core3.database.dals

import akka.pattern.pipe
import akka.util.Timeout
import core3.core.Component
import core3.database.containers.{Container, MutableContainer}
import core3.database.views.{ContainerView, QueryType}
import core3.database.{ContainerType, ObjectID}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A Database Abstraction Layer interface for defining data access actor components.
  */
abstract class DatabaseAbstractionLayerComponent(implicit ec: ExecutionContext, timeout: Timeout) extends Component {

  import DatabaseAbstractionLayerComponent._

  protected def handle_GetDatabaseIdentifier: String

  protected def handle_GetSupportedContainers: Vector[ContainerType]

  protected def handle_GetLayerType: LayerType

  protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean]

  protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean]

  protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean]

  protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]]

  protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]]

  protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container]

  protected def handle_CreateObject(container: Container): Future[Boolean]

  protected def handle_UpdateObject(container: MutableContainer): Future[Boolean]

  protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean]

  protected def handle_LoadView(view: ContainerView): Future[Unit] = {
    val requestsMap = view.fieldQueries.map {
      case (field, queryData) =>
        val currentRequest = queryData.queryType match {
          case QueryType.Custom => handle_GetCustomQueryResult(queryData.containerType, queryData.customQueryName.get, queryData.queryParams.get)
          case QueryType.Generic => handle_GetGenericQueryResult(queryData.containerType)
        }

        queryData.withFilter match {
          case Some(filter) => (field, currentRequest.map(_.filter(filter)))
          case None => (field, currentRequest)
        }
    }

    Future.traverse(requestsMap) {
      case (field, currentRequest) =>
        currentRequest.map(field -> _)
    }.map(result => view.loadFieldsData(result.toMap))
  }

  addReceiver {
    case GetDatabaseIdentifier() => sender ! handle_GetDatabaseIdentifier
    case GetSupportedContainers() => sender ! handle_GetSupportedContainers
    case GetLayerType() => sender ! handle_GetLayerType
    case VerifyDatabaseStructure(objectsType) => handle_VerifyDatabaseStructure(objectsType) pipeTo sender
    case BuildDatabaseStructure(objectsType) => handle_BuildDatabaseStructure(objectsType) pipeTo sender
    case ClearDatabaseStructure(objectsType) => handle_ClearDatabaseStructure(objectsType) pipeTo sender
    case GetGenericQueryResult(objectsType) => handle_GetGenericQueryResult(objectsType) pipeTo sender
    case GetCustomQueryResult(objectsType, customQueryName, queryParams) => handle_GetCustomQueryResult(objectsType, customQueryName, queryParams) pipeTo sender
    case LoadView(view: ContainerView) => handle_LoadView(view) pipeTo sender
    case GetObject(objectType, objectID) => handle_GetObject(objectType, objectID) pipeTo sender
    case CreateObject(container) => handle_CreateObject(container) pipeTo sender
    case UpdateObject(container) => handle_UpdateObject(container) pipeTo sender
    case DeleteObject(objectType, objectID) => handle_DeleteObject(objectType, objectID) pipeTo sender
  }
}

object DatabaseAbstractionLayerComponent {

  /**
    * Retrieves a database identification string, for information/debugging purposes.
    *
    * @return Future[String] - the identifier string
    */
  case class GetDatabaseIdentifier()

  /**
    * Retrieves a list of containers supported by the database.
    *
    * @return Future[ Vector[ContainerType] ] - the list of supported containers
    */
  case class GetSupportedContainers()

  /**
    * Retrieves the type of the Database Abstraction Layer.
    *
    * @return Future[LayerType] - the layer type
    */
  case class GetLayerType()

  /**
    * Verifies the structure of the database.
    *
    * @param objectsType denotes which database/table to be verified
    * @return Future[Boolean] - true, if the structure is valid
    */
  case class VerifyDatabaseStructure(objectsType: ContainerType)

  /**
    * Builds the the database structure.
    *
    * Note: An empty database is expected.
    *
    * @param objectsType denotes which database/table to be created
    * @return Future[Boolean] - true, if the operation was successful
    */
  case class BuildDatabaseStructure(objectsType: ContainerType)

  /**
    * Clears the database structure.
    *
    * Note: All data, tables, databases, etc, are destroyed.
    *
    * @param objectsType denotes which database/table to be removed
    * @return Future[Boolean] - true, if the operation was successful
    */
  case class ClearDatabaseStructure(objectsType: ContainerType)

  /**
    * Performs a generic database query that retrieves all data in for the specified container type.
    *
    * @param objectsType the type of objects to be queried
    * @return Future[ Vector[Container] ] - a container set based on the query results
    */
  case class GetGenericQueryResult(objectsType: ContainerType)

  /**
    * Performs a custom database query.
    *
    * @param objectsType     the type of objects to be queried
    * @param customQueryName the custom query name to be performed
    * @param queryParams     the query parameters
    * @return Future[ Vector[Container] ] - a container set based on the query results
    */
  case class GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String])

  /**
    * Performs all queries defined in the supplied view and loads the resulting data in it.
    *
    * @param view the view to load
    * @return Future[Unit] - nothing (the supplied object is updated)
    */
  case class LoadView(view: ContainerView)

  /**
    * Retrieves the specified object.
    *
    * @param objectType the type of the object to be retrieved
    * @param objectID   the ID of the object to be retrieved
    * @return Future[Container] - the requested object
    */
  case class GetObject(objectType: ContainerType, objectID: ObjectID)

  /**
    * Creates a new entry for the supplied object in the database.
    *
    * @param container the object data
    * @return Future[Boolean] - true, if the operation was successful
    */
  case class CreateObject(container: Container)

  /**
    * Updates the existing entry for the supplied object in the database.
    *
    * Note: Immutable containers cannot be updated.
    *
    * @param container the object data
    * @return Future[Boolean] - true, if the operation was successful
    */
  case class UpdateObject(container: MutableContainer)

  /**
    * Deletes the specified object form the database.
    *
    * @param objectType the type of the object to be deleted
    * @param objectID   the ID of the object
    * @return Future[Boolean] - true, if the operation was successful
    */
  case class DeleteObject(objectType: ContainerType, objectID: ObjectID)

}
