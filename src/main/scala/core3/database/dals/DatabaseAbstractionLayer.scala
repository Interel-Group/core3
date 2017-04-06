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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import core3.database.containers.{Container, ContainerSet, MutableContainer}
import core3.database.views.ContainerView
import core3.database.{ContainerType, ObjectID}

import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * A wrapper class for sending requests to [[core3.database.dals.DatabaseAbstractionLayerComponent]] actors.
  *
  * @param actor the actor to be used for all requests
  */
class DatabaseAbstractionLayer(private val actor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {

  import DatabaseAbstractionLayerComponent._

  /**
    * Retrieves the underlying [[akka.actor.ActorRef]].
    *
    * @return the actor ref
    */
  def getRef: ActorRef = actor

  /**
    * Retrieves a database identification string, for information/debugging purposes.
    *
    * @return the identifier string
    */
  def getDatabaseIdentifier: String =
    Await.result((actor ? GetDatabaseIdentifier()).mapTo[String], timeout.duration)

  /**
    * Retrieves a list of containers supported by the database.
    *
    * @return the list of supported containers
    */
  def getSupportedContainers: Vector[ContainerType] =
    Await.result((actor ? GetSupportedContainers()).mapTo[Vector[ContainerType]], timeout.duration)

  /**
    * Retrieves the type of the Database Abstraction Layer.
    *
    * @return the layer type
    */
  def getLayerType: LayerType =
    Await.result((actor ? GetLayerType()).mapTo[LayerType], timeout.duration)

  /**
    * Verifies the structure of the database.
    *
    * @param objectsType denotes which database/table to be verified
    * @return true, if the structure is valid
    */
  def verifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] =
    (actor ? VerifyDatabaseStructure(objectsType)).mapTo[Boolean]

  /**
    * Builds the the database structure.
    *
    * Note: An empty database is expected.
    *
    * @param objectsType denotes which database/table to be created
    * @return true, if the operation was successful
    */
  def buildDatabaseStructure(objectsType: ContainerType): Future[Boolean] =
    (actor ? BuildDatabaseStructure(objectsType)).mapTo[Boolean]

  /**
    * Clears the database structure.
    *
    * Note: All data, tables, databases, etc, are destroyed.
    *
    * @param objectsType denotes which database/table to be removed
    * @return true, if the operation was successful
    */
  def clearDatabaseStructure(objectsType: ContainerType): Future[Boolean] =
    (actor ? ClearDatabaseStructure(objectsType)).mapTo[Boolean]

  /**
    * Performs a generic database query that retrieves all data.
    *
    * @param objectsType the type of objects to be queried
    * @return a container set based on the query results
    */
  def queryDatabase(objectsType: ContainerType): Future[ContainerSet] =
    (actor ? GetGenericQueryResult(objectsType)).mapTo[ContainerSet]

  /**
    * Performs a custom database query.
    *
    * @param objectsType     the type of objects to be queried
    * @param customQueryName the custom query name to be performed
    * @param queryParams     the query parameters
    * @return a container set based on the query results
    */
  def queryDatabase(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[ContainerSet] =
    (actor ? GetCustomQueryResult(objectsType, customQueryName, queryParams)).mapTo[ContainerSet]

  /**
    * Performs all queries defined in the supplied view and loads the resulting data in it.
    *
    * @param view the view to load
    * @return nothing (the supplied object is updated)
    */
  def loadView(view: ContainerView)(implicit ec: ExecutionContext): Future[Unit] =
    (actor ? LoadView(view)).mapTo[Unit]

  /**
    * Retrieves the specified object.
    *
    * @param objectType the type of the object to be retrieved
    * @param objectID   the ID of the object to be retrieved
    * @return the requested object
    */
  def getObject(objectType: ContainerType, objectID: ObjectID): Future[Container] =
    (actor ? GetObject(objectType, objectID)).mapTo[Container]

  /**
    * Creates a new entry for the supplied object in the database.
    *
    * @param container the object data
    * @return true, if the operation was successful
    */
  def createObject(container: Container): Future[Boolean] =
    (actor ? CreateObject(container)).mapTo[Boolean]

  /**
    * Updates the existing entry for the supplied object in the database.
    *
    * Note: Immutable containers cannot be updated.
    *
    * @param container the object data
    * @return true, if the operation was successful
    */
  def updateObject(container: MutableContainer): Future[Boolean] =
    (actor ? UpdateObject(container)).mapTo[Boolean]

  /**
    * Deletes the specified object form the database.
    *
    * @param objectType the type of the object to be deleted
    * @param objectID   the ID of the object
    * @return true, if the operation was successful
    */
  def deleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] =
    (actor ? DeleteObject(objectType, objectID)).mapTo[Boolean]
}
