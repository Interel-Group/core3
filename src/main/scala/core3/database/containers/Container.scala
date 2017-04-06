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

import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import core3.utils.Timestamp
import play.api.libs.json._
import slick.jdbc.MySQLProfile.backend.DatabaseDef
import slick.jdbc.SQLActionBuilder

import scala.concurrent.{ExecutionContext, Future}

sealed trait JsonDataFormat

object JsonDataFormat {

  case object Full extends JsonDataFormat

  case object Partial extends JsonDataFormat

  case object Cache extends JsonDataFormat

  case object Search extends JsonDataFormat

  def fromString(value: String): JsonDataFormat = {
    value match {
      case "Full" => JsonDataFormat.Full
      case "Partial" => JsonDataFormat.Partial
      case "Cache" => JsonDataFormat.Cache
      case "Search" => JsonDataFormat.Search
    }
  }
}

sealed trait DataType

object DataType {

  case object JSON extends DataType

  case object Cache extends DataType

  case object Slick extends DataType

  case object Search extends DataType

  def fromString(value: String): DataType = {
    value match {
      case "JSON" => DataType.JSON
      case "Cache" => DataType.Cache
      case "Slick" => DataType.Slick
      case "Search" => DataType.Search
    }
  }
}

/**
  * Base container trait.
  * <br><br>
  * Note: For container implementations, use either [[core3.database.containers.ImmutableContainer]] or
  * [[core3.database.containers.MutableContainer]].
  */
trait Container {
  val id: ObjectID
  val objectType: ContainerType
}

/**
  * Container trait for update-able objects with support for revisions.
  * <br><br>
  * Note: The container itself does NOT update revision IDs and/or numbers upon field changes.
  */
trait MutableContainer extends Container {
  val created: Timestamp
  var updated: Timestamp
  var updatedBy: String
  var revision: RevisionID
  var revisionNumber: RevisionSequenceNumber
}

/**
  * Container trait for objects that are not update-able.
  */
trait ImmutableContainer extends Container

/**
  * Basic implementation & usage trait for containers.
  */
trait BasicContainerCompanion {
  /**
    * Retrieves the container's database name for the specified data type, if supported.
    *
    * @param dataType the data type
    * @return the requested database name
    * @throws IllegalArgumentException if the data type is not supported
    */
  def getDatabaseName(dataType: DataType): String

  /**
    * Checks if the supplied container matches the specified query name and parameters.
    *
    * @param queryName   the name of the query to use for the check
    * @param queryParams the parameters to use for the check
    * @param container   the container to work with
    * @return true, if the container matches the query
    * @throws NotImplementedError      if the container does not support custom query matching
    * @throws IllegalArgumentException if an invalid query name is specified
    */
  def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean
}

/**
  * Implementation & usage trait for containers supporting JSON data handling.
  */
trait JSONContainerCompanion extends BasicContainerCompanion {
  /**
    * Converts the supplied container to a JSON value.
    *
    * @param container the container to be converted
    * @param format    the JSON data format to use
    * @return the container as a JSON value
    */
  def toJsonData(container: Container, format: JsonDataFormat): JsValue

  /**
    * Converts the supplied JSON value to a container.
    *
    * @param data the JSON value to be converted
    * @return the converted container
    */
  def fromJsonData(data: JsValue): Container
}

/**
  * Usage trait for containers supporting Slick data handling.
  */
trait SlickContainerCompanion extends BasicContainerCompanion {
  def runCreateSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runDropSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runGenericQuery(query: SQLActionBuilder, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]]

  def runGet(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Container]

  def runCreate(container: Container, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runUpdate(container: MutableContainer, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runDelete(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runCustomQuery(queryName: String, queryParams: Map[String, String], db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]]
}

/**
  * Implementation trait for containers supporting Slick data handling.
  *
  * Note: Only used by containers.
  */
trait SlickContainerCompanionImpl[ContainerTupleDef] extends SlickContainerCompanion {
  protected def convertToTuple(container: Container): ContainerTupleDef

  protected def convertFromTuple(tuple: ContainerTupleDef): Container
}

/**
  * Implementation & usage trait for containers supporting search (Solr & ElasticSearch) data handling.
  */
trait SearchContainerCompanion extends JSONContainerCompanion {
  /**
    * Retrieves the container's supported search field and their corresponding data types.
    *
    * @return the requested search field
    */
  def getSearchFields: Map[String, String]
}