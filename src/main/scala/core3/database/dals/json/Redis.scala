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
package core3.database.dals.json

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.dals.{DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import play.api.libs.json._
import redis.{Cursor, RedisClient}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * A Database Abstraction Layer for accessing a Redis database.
  *
  * @param hostname            the Redis server hostname
  * @param port                the Redis service port
  * @param secret              the Redis instance secret
  * @param connectTimeout      the maximum amount of time to wait when establishing a connection (in seconds)
  * @param containerCompanions map with all registered container companion objects
  * @param databaseID          the ID of the database to be used
  * @param scanCount           the minimum number of items to wait for when performing a Redis SCAN
  */
class Redis(
  private val hostname: String,
  private val port: Int,
  private val secret: String,
  private val connectTimeout: Int,
  private val containerCompanions: Map[ContainerType, JSONContainerCompanion],
  private val databaseID: Int,
  private val scanCount: Int
)(implicit ec: ExecutionContext, timeout: Timeout, system: ActorSystem)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param containerCompanions map with all registered container companion objects
    * @param config              the config to use (if specified; default path is 'server.static.database.redis')
    * @return the new instance
    */
  def this(
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    config: Config = StaticConfig.get.getConfig("database.redis")
  )(implicit ec: ExecutionContext, timeout: Timeout, system: ActorSystem) =
    this(
      config.getString("hostname"),
      config.getInt("port"),
      config.getString("secret"),
      config.getInt("connectionTimeout"),
      containerCompanions,
      config.getInt("databaseID"),
      config.getInt("scanCount")
    )

  private val client = RedisClient(
    hostname,
    port,
    password = Some(secret),
    db = Some(databaseID),
    connectTimeout = Some(connectTimeout.seconds)
  )

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  private def getKeyPrefix(containerType: ContainerType) = {
    s"${containerCompanions(containerType).getDatabaseName(DataType.JSON)}:"
  }

  private def getKey(containerType: ContainerType, containerID: ObjectID) = {
    s"${getKeyPrefix(containerType)}$containerID"
  }

  override protected def handle_GetDatabaseIdentifier: String = s"$databaseID@$hostname:$port"

  override protected def handle_GetLayerType: LayerType = LayerType.Redis

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerCompanions.keys.toVector

  override protected def shutdown(): Unit = {
    client.stop()
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.Redis::handle_VerifyDatabaseStructure > Object type [$objectsType] is not supported."
    )

    client.ping().map {
      c => c.toLowerCase() == "pong"
    }
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.Redis::handle_BuildDatabaseStructure > Object type [$objectsType] is not supported."
    )

    client.ping().map {
      c => c.toLowerCase() == "pong"
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.Redis::handle_ClearDatabaseStructure > Object type [$objectsType] is not supported."
    )

    for {
      keys <- client.keys(s"${getKeyPrefix(objectsType)}*")
      _ <- if (keys.nonEmpty) client.del(keys: _*) else Future.successful(0L)
    } yield {
      true
    }
  }

  private def processScan(
    objectsType: ContainerType,
    current: Cursor[scala.Seq[String]],
    previousData: ArrayBuffer[String]
  ): Future[ArrayBuffer[String]] = {
    previousData ++= current.data

    if (current.index > 0) {
      client.scan(current.index, matchGlob = Some(s"${getKeyPrefix(objectsType)}*"), count = Some(scanCount)).flatMap {
        next =>
          processScan(objectsType, next, previousData)
      }
    } else {
      Future.successful(previousData)
    }
  }

  /**
    * Retrieves all containers from the database.
    *
    * @param objectsType the database to query
    * @param companion   JSON companion object for the specified object type
    * @return the retrieved containers
    */
  private def getAllContainers(objectsType: ContainerType, companion: JSONContainerCompanion): Future[Vector[Container]] = {
    for {
      result <- client.scan(cursor = 0, matchGlob = Some(s"${getKeyPrefix(objectsType)}*"), count = Some(scanCount))
      keys <- processScan(objectsType, result, ArrayBuffer[String]())
      containers <-
      if (keys.nonEmpty) {
        client.mget(keys: _*)
          .map(_.flatten)
          .map {
            strings => strings.map(c => companion.fromJsonData(Json.parse(c.utf8String)))
          }
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      containers.toVector
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
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[ContainerSet] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.Redis::handle_GetGenericQueryResult > Object type [$objectsType] is not supported."
    )

    count_GenericQuery += 1

    val companion = containerCompanions(objectsType)

    for {
      containers <- getAllContainers(objectsType, companion)
    } yield {
      ContainerSet(objectsType, containers)
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[ContainerSet] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.Redis::handle_GetCustomQueryResult > Object type [$objectsType] is not supported."
    )

    count_CustomQuery += 1

    val companion = containerCompanions(objectsType)

    for {
      containers <- getAllContainers(objectsType, companion).map {
        containers =>
          containers.filter {
            current =>
              companion.matchCustomQuery(customQueryName, queryParams, current)
          }
      }
    } yield {
      ContainerSet(objectsType, containers)
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    assert(
      containerCompanions.contains(objectType),
      s"core3.database.dals.json.Redis::handle_GetObject > Object type [$objectType] is not supported."
    )

    count_Get += 1

    for {
      result <- client.get(getKey(objectType, objectID))
    } yield {
      val parsedResult = result.map(c => containerCompanions(objectType).fromJsonData(Json.parse(c.utf8String)))

      parsedResult match {
        case Some(container) => container
        case None => throw new RuntimeException(s"core3.database.dals.json.Redis::handle_GetObject > " +
          s"No result returned for container [$objectID] of type [$objectType].")
      }
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    assert(
      containerCompanions.contains(container.objectType),
      s"core3.database.dals.json.Redis::handle_CreateObject > Object type [${container.objectType}] is not supported."
    )

    count_Create += 1

    for {
      result <- client.set(
        getKey(container.objectType, container.id),
        Json.stringify(containerCompanions(container.objectType).toJsonData(container, JsonDataFormat.Full)),
        NX = true //only set value if it does not exist
      )
    } yield {
      result
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    assert(
      containerCompanions.contains(container.objectType),
      s"core3.database.dals.json.Redis::handle_UpdateObject > Object type [${container.objectType}] is not supported."
    )

    count_Update += 1

    for {
      result <- client.set(
        getKey(container.objectType, container.id),
        Json.stringify(containerCompanions(container.objectType).toJsonData(container, JsonDataFormat.Full)),
        XX = true //only set value if it exists
      )
    } yield {
      result
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectType),
      s"core3.database.dals.json.Redis::handle_DeleteObject > Object type [$objectType] is not supported."
    )

    count_Delete += 1

    for {
      result <- client.del(getKey(objectType, objectID))
    } yield {
      result == 1
    }
  }
}

object Redis extends ComponentCompanion {
  def props(
    hostname: String,
    port: Int,
    secret: String,
    connectTimeout: Int,
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    databaseID: Int,
    scanCount: Int
  )(implicit ec: ExecutionContext, timeout: Timeout, system: ActorSystem): Props = Props(
    classOf[Redis],
    hostname,
    port,
    secret,
    connectTimeout,
    containerCompanions,
    databaseID,
    scanCount,
    ec,
    timeout,
    system
  )

  def props(
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout, system: ActorSystem): Props = Props(
    classOf[Redis],
    containerCompanions,
    config,
    ec,
    timeout,
    system
  )

  def props(
    containerCompanions: Map[ContainerType, JSONContainerCompanion]
  )(implicit ec: ExecutionContext, timeout: Timeout, system: ActorSystem): Props = Props(
    classOf[Redis],
    containerCompanions,
    StaticConfig.get.getConfig("database.redis"),
    ec,
    timeout,
    system
  )

  override def getActionDescriptors: Seq[ActionDescriptor] = {
    Seq(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
