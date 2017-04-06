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

import akka.actor.{ActorSystem, AddressFromURIString, Props}
import akka.cluster.Cluster
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.dals.memory.utils.{CacheStore, ClusterMessenger}
import core3.database.dals.{DatabaseAbstractionLayer, DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * A Database Abstraction Layer for wrapping another layer with a clustering-capable cache.
  * <br><br>
  * Notes:
  * <ul>
  * <li>A single node cluster can be created by assigning the same local and cluster ports.</li>
  * <li>The first node to start up in a multi-node cluster must be configured to bind to itself (in the same way as
  * a single-node cluster configuration).</li>
  * <li>The initial message synchronization between cluster nodes is configured based on the specified [[syncInterval]]
  * plus a random number (0-5) of seconds, to try to avoid multiple synchronizations happening at the same time.</li>
  * <li>If [[preload]] is set to true, the actual preload will be started after a delay, based on [[actionTimeout]].</li>
  * <li>The Akka config option 'akka.remote.netty.tcp.port' is always overridden by the parameter [[localPort]].</li>
  * <li>The Akka config option 'akka.actor.provider' is always overridden by the parameter [[actorProvider]].</li>
  * <li>TLS support for cluster communication is enabled via Netty config. Actual settings can be found in Akka's
  * Remoting docs (http://doc.akka.io/docs/akka/current/scala/remoting.html).</li>
  * </ul>
  *
  * @param clusterHost               cluster hostname/IP; can be set to own/local hostname/IP to initialize cluster or create single-node cluster
  * @param clusterPort               cluster port; can be set to own/local port to initialize cluster or create single-node cluster
  * @param localPort                 local actor system TCP port
  * @param source                    source DAL for retrieving and updating data; after the cache is created, all changes must be sent to
  *                                  the cache and not to the source directly
  * @param preload                   if set to true, the cache will attempt to load all data during initialization (see notes)
  * @param actionTimeout             the amount of time (in seconds) before an operation is considered as timed out
  * @param containerCompanions       map with all registered container companion objects
  * @param containerTypeMaxCacheSize maximum cache size per container type
  * @param syncInterval              message sync broadcast interval; also used for initial sync delay (see notes)
  * @param maxLoadAttempts           maximum number of times to attempt loading an object from the source
  * @param actorProvider             Akka actor provider
  * @param ec                        implicit execution context
  */
class DistributedCache(
  private val clusterHost: String,
  private val clusterPort: Int,
  private val localPort: Int,
  private val source: DatabaseAbstractionLayer,
  private val preload: Boolean,
  private val actionTimeout: Int,
  private val containerCompanions: Map[ContainerType, BasicContainerCompanion],
  private val containerTypeMaxCacheSize: Int,
  private val syncInterval: Int,
  private val maxLoadAttempts: Int,
  private val actorProvider: String
)
  (implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param source              source DAL for retrieving and updating data; after the cache is created, all changes must be sent to
    *                            the cache and not to the source directly
    * @param containerCompanions map with all registered container companion objects
    * @param config              the config to use (if specified; default path is 'server.static.database.distributed-cache')
    * @return the new instance
    */
  def this(
    source: DatabaseAbstractionLayer,
    containerCompanions: Map[ContainerType, BasicContainerCompanion],
    config: Config = StaticConfig.get.getConfig("database.distributed-cache")
  )(implicit ec: ExecutionContext, timeout: Timeout) =
    this(
      config.getString("clusterHost"),
      config.getInt("clusterPort"),
      config.getInt("localPort"),
      source,
      config.getBoolean("preload"),
      config.getInt("actionTimeout"),
      containerCompanions,
      config.getInt("containerTypeMaxCacheSize"),
      config.getInt("syncInterval"),
      config.getInt("maxLoadAttempts"),
      config.getString("actorProvider")
    )

  private val config = ConfigFactory.load()
    .withValue("akka.actor.provider", ConfigValueFactory.fromAnyRef(actorProvider))
    .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(localPort))
  private val systemName = "core3_database_dals_memory__DistributedCache"
  private val localSystem = ActorSystem(systemName, config)
  private val cluster = Cluster(localSystem)

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  private val clusterAddress = if (clusterPort == localPort) {
    cluster.selfAddress
  } else {
    AddressFromURIString(s"akka.tcp://$systemName@$clusterHost:$clusterPort")
  }

  private val store = localSystem.actorOf(
    CacheStore.props(source, containerCompanions, containerTypeMaxCacheSize, actionTimeout, maxLoadAttempts, preload)
  )

  private val messenger = localSystem.actorOf(
    ClusterMessenger.props(
      s"DistributedCache@$clusterHost:$clusterPort",
      store,
      syncInterval + scala.util.Random.nextInt(5),
      syncInterval
    )
  )

  cluster.join(clusterAddress)

  override protected def handle_GetDatabaseIdentifier: String = s"core3.database.dals.memory.DistributedCache[${source.getDatabaseIdentifier}]"

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = source.getSupportedContainers

  override protected def handle_GetLayerType: LayerType = LayerType.DistributedCache

  override def shutdown(): Unit = {
    Await.result(
      for {
        _ <- messenger ? ClusterMessenger.StopMessenger()
        _ <- Future.successful(cluster.leave(cluster.selfAddress))
        _ <- localSystem.terminate()
      } yield {},
      actionTimeout.seconds)
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    source.verifyDatabaseStructure(objectsType)
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    source.buildDatabaseStructure(objectsType)
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    for {
      _ <- store ? CacheStore.Clear(objectsType)
      result <- source.clearDatabaseStructure(objectsType)
    } yield {
      result
    }
  }

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    action.toLowerCase match {
      case "stats" =>

        Future.successful {
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "layerType" -> handle_GetLayerType.toString,
                "id" -> handle_GetDatabaseIdentifier,
                "cluster" -> Json.obj(
                  "address" -> clusterAddress.toString,
                  "state" -> cluster.state.toString
                ),
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

      case "reload" =>
        (params match {
          case Some(actualParams) =>
            actualParams.get("objectsType").flatten match {
              case Some(objectsType) => store ? CacheStore.Load(Some(objectsType))
              case None => Future.successful(
                ActionResult(
                  wasSuccessful = false,
                  message = Some(s"Unexpected parameter(s) supplied: [${actualParams.keys.mkString(", ")}]")
                )
              )
            }

          case None =>
            store ? CacheStore.Load(objectsTypeOpt = None)
        }).map {
          _ => ActionResult(wasSuccessful = true)
        }.recover {
          case e =>
            ActionResult(wasSuccessful = false, message = Some(s"Exception encountered: [${e.getMessage}]"))
        }
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[ContainerSet] = {
    count_GenericQuery += 1
    (store ? CacheStore.GenericQuery(objectsType)).mapTo[ContainerSet]
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[ContainerSet] = {
    count_CustomQuery += 1
    (store ? CacheStore.CustomQuery(objectsType, customQueryName, queryParams)).mapTo[ContainerSet]
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    count_Get += 1
    (store ? CacheStore.Get(objectType, objectID)).mapTo[Container]
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    count_Create += 1

    (store ? CacheStore.Create(container)).mapTo[Boolean].map {
      result =>
        if (result) {
          val (revision, revisionNumber) = container match {
            case (c: MutableContainer) => (Some(c.revision), Some(c.revisionNumber))
            case (_: ImmutableContainer) => (None, None)
          }

          messenger ! ClusterMessenger.NotifyCluster(
            ClusterMessenger.UpdateType.Create,
            container.objectType,
            container.id,
            revision,
            revisionNumber
          )
        }

        result
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    count_Update += 1

    (store ? CacheStore.Update(container)).mapTo[Boolean].map {
      result =>
        if (result) {
          messenger ! ClusterMessenger.NotifyCluster(
            ClusterMessenger.UpdateType.Update,
            container.objectType,
            container.id,
            Some(container.revision),
            Some(container.revisionNumber)
          )
        }

        result
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    count_Delete += 1

    (store ? CacheStore.Delete(objectType, objectID)).mapTo[Boolean].map {
      result =>
        if (result) {
          messenger ! ClusterMessenger.NotifyCluster(
            ClusterMessenger.UpdateType.Delete,
            objectType,
            objectID,
            None,
            None
          )
        }

        result
    }
  }
}

object DistributedCache extends ComponentCompanion {
  def props(
    clusterHost: String,
    clusterPort: Int,
    localPort: Int,
    source: DatabaseAbstractionLayer,
    preload: Boolean,
    actionTimeout: Int,
    containerCompanions: Map[ContainerType, BasicContainerCompanion],
    containerTypeMaxCacheSize: Int,
    syncInterval: Int,
    maxLoadAttempts: Int = 5,
    actorProvider: String = "akka.cluster.ClusterActorRefProvider"
  )(implicit ec: ExecutionContext, timeout: Timeout = Timeout(actionTimeout.seconds)): Props = Props(
    classOf[DistributedCache],
    clusterHost,
    clusterPort,
    localPort,
    source,
    preload,
    actionTimeout,
    containerCompanions,
    containerTypeMaxCacheSize,
    syncInterval,
    maxLoadAttempts,
    actorProvider,
    ec,
    timeout
  )

  def props(
    source: DatabaseAbstractionLayer,
    containerCompanions: Map[ContainerType, BasicContainerCompanion],
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[DistributedCache],
    source,
    containerCompanions,
    config,
    ec,
    timeout
  )

  def props(
    source: DatabaseAbstractionLayer,
    containerCompanions: Map[ContainerType, BasicContainerCompanion]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[DistributedCache],
    source,
    containerCompanions,
    StaticConfig.get.getConfig("database.distributed-cache"),
    ec,
    timeout
  )

  override def getActionDescriptors: Seq[ActionDescriptor] = {
    Seq(
      ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None),
      ActionDescriptor("reload", "Reloads the cache for the specified objects/container type", arguments = Some(Map("objectsType" -> "(optional) [<container type>]")))
    )
  }
}
