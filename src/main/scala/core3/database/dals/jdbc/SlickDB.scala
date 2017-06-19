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
package core3.database.dals.jdbc

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
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * A Database Abstraction Layer for accessing JDBC-based Slick databases.
  *
  * @param containerDefinitions all configured container definitions
  * @param jdbcURL              JDBC URL for the database to be used
  * @param username             (optional) the DB user to be used for authentication
  * @param password             (optional) the DB user password
  */
abstract class SlickDB(
  private val containerDefinitions: ContainerDefinitions[BasicContainerDefinition with SlickContainerDefinition],
  private val jdbcURL: String,
  private val username: Option[String],
  private val password: Option[String]
)(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {
  protected def withProfile: JdbcProfile
  protected lazy val profile: JdbcProfile = withProfile

  import profile.api._
  private val db = Database.forURL(jdbcURL, username.orNull, password.orNull)

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

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerDefinitions.supportedContainers

  override protected def handle_GetLayerType: LayerType = LayerType.SlickDB

  /**
    * Retrieves the database name for the specified container type.
    *
    * @param objectType the container type
    * @return the requested database name
    */
  private def getDatabaseName(objectType: ContainerType): String = {
    containerDefinitions(objectType).getDatabaseName.replaceAll("[^A-Za-z0-9]", "_")
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
      db.run(containerDefinitions(objectsType).createSchemaAction()).map(_ => true)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    try {
      db.run(containerDefinitions(objectsType).dropSchemaAction()).map(_ => true)
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
                "supportedContainers" -> handle_GetSupportedContainers,
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
      count_GenericQuery += 1

      db.run(containerDefinitions(objectsType).genericQueryAction).map(_.toVector)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    try {
      count_CustomQuery += 1

      db.run(containerDefinitions(objectsType).customQueryAction(customQueryName, queryParams)).map(_.toVector)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    try {
      count_Get += 1

      db.run(containerDefinitions(objectType).getAction(objectID)).map(_.head)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    try {
      count_Create += 1

      db.run(containerDefinitions(container.objectType).createAction(container)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    try {
      count_Update += 1

      db.run(containerDefinitions(container.objectType).updateAction(container)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    try {
      count_Delete += 1

      db.run(containerDefinitions(objectType).deleteAction(objectID)).map(_ == 1)
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }
}

object SlickDB extends ComponentCompanion {
  def props(
    definitions: ContainerDefinitions[BasicContainerDefinition with SlickContainerDefinition],
    jdbcProfile: JdbcProfile,
    jdbcURL: String,
    username: Option[String],
    password: Option[String]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    new SlickDB(definitions, jdbcURL, username, password) {
      override protected def withProfile: JdbcProfile = jdbcProfile
    }
  )

  def props(
    definitions: ContainerDefinitions[BasicContainerDefinition with SlickContainerDefinition],
    jdbcProfile: JdbcProfile,
    config: Config = StaticConfig.get.getConfig("database.slick")
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    new SlickDB(definitions,
      config.getString("url"),
      if (config.hasPath("username")) Some(config.getString("username")) else None,
      if (config.hasPath("password")) Some(config.getString("password")) else None
    ) {
      override protected def withProfile: JdbcProfile = jdbcProfile
    }
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
