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
package core3.database.dals.sql

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.dals.{DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import play.api.libs.json.Json
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.meta.MTable

import scala.concurrent._
import scala.util.control.NonFatal

/**
  * A Database Abstraction Layer for accessing a MariaDB database.
  *
  * @constructor creates a new MariaDB DAL
  * @param databaseName        the name of the database to be used
  * @param username            the DB user to be used for authentication
  * @param password            the password for the DB user
  * @param containerCompanions map with all registered container companion objects
  */
class MariaDB(
  private val databaseName: String,
  private val username: String,
  private val password: String,
  private val containerCompanions: Map[ContainerType, SlickContainerCompanion]
)(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param containerCompanions map with all registered container companion objects
    * @param config              the config to use (if specified; default path is 'server.static.database.mariadb')
    * @return the new instance
    */
  def this(
    containerCompanions: Map[ContainerType, SlickContainerCompanion],
    config: Config = StaticConfig.get.getConfig("database.mariadb")
  )(implicit ec: ExecutionContext, timeout: Timeout) =
    this(
      config.getString("databaseName"),
      config.getString("username"),
      config.getString("password"),
      containerCompanions
    )

  private val jdbcURL = s"jdbc:mariadb://localhost:3306/$databaseName"
  private val db = Database.forURL(jdbcURL, username, password)

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  override def shutdown(): Unit = {
    db.close()
  }

  override protected def handle_GetDatabaseIdentifier: String = jdbcURL

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerCompanions.keys.toVector

  override protected def handle_GetLayerType: LayerType = LayerType.MariaDB

  /**
    * Retrieves the database name for the specified container type.
    *
    * @param objectType the container type
    * @return the requested database name
    */
  private def getDatabaseName(objectType: ContainerType): String = {
    assert(containerCompanions.contains(objectType), s"core3.database.dals.sql.MariaDB::getDatabaseName > Object type [$objectType] is not supported.")
    containerCompanions(objectType).getDatabaseName.replaceAll("[^A-Za-z0-9]", "_")
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    db.run(MTable.getTables).map {
      result =>
        result.exists {
          current =>
            current.name.name.toLowerCase == getDatabaseName(objectsType).toLowerCase
        }
    }
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    try {
      assert(containerCompanions.contains(objectsType), s"core3.database.dals.sql.MariaDB::buildDatabaseStructure > Object type [$objectsType] is not supported.")
      db.run(containerCompanions(objectsType).createSchemaAction()).map(_ => true)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    try {
      assert(containerCompanions.contains(objectsType), s"core3.database.dals.sql.MariaDB::handle_ClearDatabaseStructure > Object type [$objectsType] is not supported.")
      db.run(containerCompanions(objectsType).dropSchemaAction()).map(_ => true)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future.successful {
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "layerType" -> handle_GetLayerType,
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
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    try {
      assert(containerCompanions.contains(objectsType), s"core3.database.dals.sql.MariaDB::handle_GetGenericQueryResult > Object type [$objectsType] is not supported.")

      count_GenericQuery += 1

      db.run(containerCompanions(objectsType).genericQueryAction).map(_.toVector)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    try {
      assert(containerCompanions.contains(objectsType), s"core3.database.dals.sql.MariaDB::handle_GetCustomQueryResult > Object type [$objectsType] is not supported.")

      count_CustomQuery += 1

      db.run(containerCompanions(objectsType).customQueryAction(customQueryName, queryParams)).map(_.toVector)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    try {
      assert(containerCompanions.contains(objectType), s"core3.database.dals.sql.MariaDB::handle_GetObject > Object type [$objectType] is not supported.")

      count_Get += 1

      db.run(containerCompanions(objectType).getAction(objectID)).map(_.head)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    try {
      assert(containerCompanions.contains(container.objectType), s"core3.database.dals.sql.MariaDB::handle_CreateObject > Object type [${container.objectType}] is not supported.")

      count_Create += 1

      db.run(containerCompanions(container.objectType).createAction(container)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    try {
      assert(containerCompanions.contains(container.objectType), s"core3.database.dals.sql.MariaDB::handle_UpdateObject > Object type [${container.objectType}] is not supported.")

      count_Update += 1

      db.run(containerCompanions(container.objectType).updateAction(container)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    try {
      assert(containerCompanions.contains(objectType), s"core3.database.dals.sql.MariaDB::handle_DeleteObject > Object type [$objectType] is not supported.")

      count_Delete += 1

      db.run(containerCompanions(objectType).deleteAction(objectID)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }
}

object MariaDB extends ComponentCompanion {
  def props(
    databaseName: String,
    dbUser: String,
    dbUserPassword: String,
    containerCompanions: Map[ContainerType, SlickContainerCompanion]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[MariaDB],
    databaseName,
    dbUser,
    dbUserPassword,
    containerCompanions,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, SlickContainerCompanion],
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[MariaDB],
    containerCompanions,
    config,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, SlickContainerCompanion]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[MariaDB],
    containerCompanions,
    StaticConfig.get.getConfig("database.mariadb"),
    ec,
    timeout
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
