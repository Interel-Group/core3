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
package core3.database.dals.memory.utils

import akka.actor.{Actor, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import core3.database.containers._
import core3.database.dals.DatabaseAbstractionLayer
import core3.database.views.ContainerView
import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import play.api.Logger

import scala.collection.{immutable, mutable}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Akka Actor for container storage and caching.
  * <br><br>
  * Notes:
  * <ul>
  * <li>If [[preload]] is set to true, the loading operation will be executed with a delay based on [[actionTimeout]].</li>
  * <ul>
  *
  * @param source                    the source DAL to use for data retrieval and updates
  * @param containerDefinitions      all configured container definitions
  * @param containerTypeMaxCacheSize maximum cache size per container type
  * @param actionTimeout             the amount of time (in seconds) before an operation is considered as timed out
  * @param maxLoadAttempts           maximum number of times to attempt loading an object from the source
  * @param preload                   if set to true, the cache will attempt to load all data during initialization
  */
class CacheStore(
  private val source: DatabaseAbstractionLayer,
  private val containerDefinitions: ContainerDefinitions[BasicContainerDefinition],
  private val containerTypeMaxCacheSize: Int,
  private val actionTimeout: Int,
  private val maxLoadAttempts: Int,
  private val preload: Boolean
)
  (implicit ec: ExecutionContext)
  extends Actor {

  import CacheStore.RevisionState

  private type ContainerCache = Cache[ObjectID, Container]
  private type ContainerViewCache = Cache[ObjectID, ContainerView]

  private case class CacheState(store: ContainerCache, evicted: mutable.HashSet[ObjectID])

  implicit val timeout = Timeout(actionTimeout.seconds)
  private val auditLogger = Logger("audit")

  private val caches: immutable.Map[ContainerType, CacheState] =
    source.getSupportedContainers.map {
      containerType =>
        containerType -> CacheState(
          Scaffeine()
            .recordStats()
            .maximumSize(containerTypeMaxCacheSize)
            .removalListener[ObjectID, Container](entryRemovalHandler)
            .build(),
          mutable.HashSet.empty
        )
    }.toMap

  private val refs: immutable.Map[ContainerType, mutable.HashMap[ObjectID, ContainerRef[Container]]] =
    source.getSupportedContainers.map(_ -> mutable.HashMap.empty[ObjectID, ContainerRef[Container]]).toMap

  if (preload) {
    context.system.scheduler.scheduleOnce(actionTimeout.seconds, self, CacheStore.Load(objectsTypeOpt = None))
    auditLogger.info(s"core3.database.dals.memory.utils.CacheStore::() > Scheduled data preload in [$actionTimeout] seconds.")
  }

  /**
    * Handler for managing cache changes/removals.
    *
    * @param id        the ID of the object that was changed/removed
    * @param container the container object that was changed/removed
    * @param reason    the reason for the change/removal
    */
  private def entryRemovalHandler(id: ObjectID, container: Container, reason: RemovalCause): Unit = {
    val refsStore = refs(container.objectType)
    reason match {
      case RemovalCause.SIZE | RemovalCause.COLLECTED | RemovalCause.EXPIRED =>
        caches(container.objectType).evicted += id
        refsStore.get(id).foreach(_.drop(containerRemoved = false))
      case RemovalCause.REPLACED | RemovalCause.EXPLICIT => //do nothing
    }
  }

  /**
    * Determines the current revision differences between the supplied container and the specified revision and revision number.
    *
    * @param container      the container to be checked
    * @param revision       the required revision (if specified)
    * @param revisionNumber the required revision number (if specified)
    * @return the current revision state
    */
  private def getRevisionState(
    container: Container,
    revision: Option[RevisionID],
    revisionNumber: Option[RevisionSequenceNumber]
  ): RevisionState = {
    container match {
      case (mutable: MutableContainer) =>
        if (revision.isDefined && revisionNumber.isDefined) {
          if (mutable.revision == revision.get) {
            if (mutable.revisionNumber == revisionNumber.get) RevisionState.InSync
            else RevisionState.OutOfSync
          } else {
            if (mutable.revisionNumber == revisionNumber.get) RevisionState.OutOfSync
            else if (mutable.revisionNumber > revisionNumber.get) RevisionState.ContainerAhead
            else RevisionState.ContainerBehind
          }
        } else {
          RevisionState.InvalidData
        }
      case (_: ImmutableContainer) =>
        if (revision.isEmpty && revisionNumber.isEmpty) RevisionState.NotNeeded
        else RevisionState.InvalidData
      case _ =>
        RevisionState.InvalidData
    }
  }

  /**
    * Retrieves the stats for all caches.
    *
    * @return a map containing cache stats for each container type
    */
  def getStats: immutable.Map[ContainerType, CacheStats] = caches.map {
    case (objectsType, state) =>
      objectsType -> state.store.stats()
  }

  override def receive: Receive = {
    case CacheStore.GetAllContainers(objectsType) =>
      val result: Future[Vector[Container]] = handle_GetAllContainers(objectsType)
      result pipeTo sender

    case CacheStore.BulkUpdateState(objectsType, containers) =>
      handle_BulkUpdateState(objectsType, containers)
      sender ! Unit

    case CacheStore.UpdateState(container) =>
      handle_UpdateState(container)
      sender ! Unit

    case CacheStore.DeleteState(objectType, objectID) =>
      handle_DeleteState(objectType, objectID)
      sender ! Unit

    case CacheStore.LoadObject(objectType, objectID, revisionID, revisionSequenceNumber, attemptNumber) =>
      handle_LoadObject(objectType, objectID, revisionID, revisionSequenceNumber, attemptNumber)
      sender ! Unit

    case CacheStore.Load(objectsTypeOpt) =>
      handle_Load(objectsTypeOpt)
      sender ! Unit

    case CacheStore.Clear(objectType) =>
      handle_Clear(objectType)
      sender ! Unit

    case CacheStore.Get(objectType, objectID) =>
      val result: Future[Container] = handle_Get(objectType, objectID)
      result pipeTo sender

    case CacheStore.Create(container) =>
      val result: Future[Boolean] = handle_Create(container)
      result pipeTo sender

    case CacheStore.Update(container) =>
      val result: Future[Boolean] = handle_Update(container)
      result pipeTo sender

    case CacheStore.Delete(objectType, objectID, sourceUpdated) =>
      val result: Future[Boolean] = handle_Delete(objectType, objectID, sourceUpdated)
      result pipeTo sender

    case CacheStore.GenericQuery(objectsType) =>
      val result: Future[Vector[Container]] = handle_GenericQuery(objectsType)
      result pipeTo sender

    case CacheStore.CustomQuery(objectsType, customQueryName, queryParams) =>
      val result: Future[Vector[Container]] = handle_CustomQuery(objectsType, customQueryName, queryParams)
      result pipeTo sender
  }

  //
  // Message Handlers
  //

  private def handle_GetAllContainers(objectsType: ContainerType): Future[Vector[Container]] = {
    //note: access to the evicted objects (cacheState.evicted) and the refs is not synchronized because it is
    //acceptable and expected for these lists to not be up-to-date when update operations are being executed
    val cacheState = caches(objectsType)
    val canCache = (cacheState.store.estimatedSize() + cacheState.evicted.size) < containerTypeMaxCacheSize

    for {
      missingContainers <- if (cacheState.evicted.nonEmpty) {
        source.queryDatabase(objectsType).map(_.filter(c => cacheState.evicted.contains(c.id)))
      } else {
        Future.successful(Vector.empty)
      }
      containers <- if (missingContainers.nonEmpty && canCache) {
        (self ? CacheStore.BulkUpdateState(objectsType, missingContainers)).map {
          _ => refs(objectsType).values.flatten.toVector
        }
      } else {
        Future.successful(refs(objectsType).values.flatten.toVector ++ missingContainers)
      }
    } yield {
      containers
    }
  }

  private def handle_BulkUpdateState(objectsType: ContainerType, containers: Vector[Container]): Unit = {
    val cacheState = caches(objectsType)
    val refsStore = refs(objectsType)
    containers.foreach {
      container =>
        cacheState.store.put(container.id, container)
        cacheState.evicted -= container.id

        refsStore.get(container.id) match {
          case Some(existingRef) => existingRef.replace(container)
          case None => refsStore += container.id -> ContainerRef(container)
        }
    }
  }

  private def handle_UpdateState(container: Container): Unit = {
    val cacheState = caches(container.objectType)
    cacheState.store.put(container.id, container)
    cacheState.evicted -= container.id

    val refsStore = refs(container.objectType)
    refsStore.get(container.id) match {
      case Some(existingRef) => existingRef.replace(container)
      case None => refsStore += container.id -> ContainerRef(container)
    }
  }

  private def handle_DeleteState(objectType: ContainerType, objectID: ObjectID): Unit = {
    caches(objectType).store.invalidate(objectID)
    refs(objectType).remove(objectID).foreach(_.drop(containerRemoved = true))
  }

  private def handle_LoadObject(
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID],
    revisionSequenceNumber: Option[RevisionSequenceNumber],
    attemptNumber: Int
  ): Unit = {
    val ref = refs.get(objectType).flatMap(_.get(objectID))
    val isCached = ref.exists(_.isDefined)

    if (isCached) {
      getRevisionState(ref.get.get, revisionID, revisionSequenceNumber) match {
        case RevisionState.InSync => //do nothing
        case RevisionState.ContainerAhead => //do nothing
        case RevisionState.NotNeeded => //do nothing
        case RevisionState.ContainerBehind =>
          if (attemptNumber < maxLoadAttempts) {
            akka.pattern.after((attemptNumber + 1).seconds, using = context.system.scheduler)(
              for {
                container <- source.getObject(objectType, objectID)
                _ <- self ? CacheStore.UpdateState(container)
              } yield {
                self ! CacheStore.LoadObject(objectType, objectID, revisionID, revisionSequenceNumber, attemptNumber + 1)
              }
            )
          } else {
            val container = ref.get.get.asInstanceOf[MutableContainer]
            auditLogger.error(s"core3.database.dals.memory.utils.CacheStore::handle_LoadObject > " +
              s"Failed to load object [$objectType] with ID [$objectID] after [$maxLoadAttempts] attempts; " +
              s"last attempt found container at revision [${container.revision}] with RSN [${container.revisionNumber}]).")
          }
        case RevisionState.OutOfSync =>
          val container = ref.get.get.asInstanceOf[MutableContainer]
          auditLogger.error(s"core3.database.dals.memory.utils.CacheStore::handle_LoadObject > " +
            s"Failed to load object [$objectType] with ID [$objectID]; " +
            s"the expected revision [${revisionID.get}] with RSN [${revisionSequenceNumber.get}] " +
            s"did not match the container revision [${container.revision}] with RSN [${container.revisionNumber}].")
        case RevisionState.InvalidData =>
          val container = ref.get.get
          auditLogger.error(s"core3.database.dals.memory.utils.CacheStore::handle_LoadObject > " +
            s"Failed to load object [${container.objectType}] with ID [${container.id}]; " +
            s"input data for revision [$revisionID] and RSN [$revisionSequenceNumber] was not expected.")
      }
    } else {
      if (attemptNumber < maxLoadAttempts) {
        akka.pattern.after((attemptNumber + 1).seconds, using = context.system.scheduler)(
          for {
            container <- source.getObject(objectType, objectID)
            _ <- self ? CacheStore.UpdateState(container)
          } yield {
            self ! CacheStore.LoadObject(objectType, objectID, revisionID, revisionSequenceNumber, attemptNumber + 1)
          }
        )
      } else {
        auditLogger.error(s"core3.database.dals.memory.utils.CacheStore::handle_LoadObject > " +
          s"Failed to load object [$objectType] with ID [$objectID] after [$maxLoadAttempts] attempts; object was never cached.")
      }
    }
  }

  private def handle_Load(objectsTypeOpt: Option[ContainerType]): Unit = {
    objectsTypeOpt match {
      case Some(objectType) => source.queryDatabase(objectType).map {
        containers =>
          if (containers.nonEmpty) {
            auditLogger.info(s"core3.database.dals.memory.utils.CacheStore::handle_Load > " +
              s"Loading [${containers.size}] [$objectType] containers into cache.")
            self ! CacheStore.BulkUpdateState(objectType, containers)
          } else {
            auditLogger.info(s"core3.database.dals.memory.utils.CacheStore::handle_Load > No [$objectType] containers found for loading.")
          }
      }

      case None => source.getSupportedContainers.foreach {
        objectType =>
          self ! CacheStore.Load(Some(objectType))
      }
    }
  }

  private def handle_Clear(objectType: ContainerType): Unit = {
    val cacheState = caches(objectType)
    val refsStore = refs(objectType)

    cacheState.store.invalidateAll()
    cacheState.evicted.clear()
    refsStore.values.foreach(_.drop(containerRemoved = true))
    refsStore.clear()
    auditLogger.info(s"core3.database.dals.memory.utils.CacheStore::handle_Clear > Cache for [$objectType] containers cleared.")
  }

  private def handle_Get(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    caches(objectType).store.getIfPresent(objectID) match {
      case Some(container) =>
        Future.successful(container)
      case None =>
        source.getObject(objectType, objectID).map {
          container =>
            self ! CacheStore.UpdateState(container)
            container
        }
    }
  }

  private def handle_Create(container: Container): Future[Boolean] = {
    for {
      result <- source.createObject(container)
      _ <- if (result) {
        self ? CacheStore.UpdateState(container)
      } else Future.successful(Unit)
    } yield {
      result
    }
  }

  private def handle_Update(container: MutableContainer): Future[Boolean] = {
    for {
      result <- source.updateObject(container)
      _ <- if (result) {
        self ? CacheStore.UpdateState(container)
      } else Future.successful(Unit)
    } yield {
      result
    }
  }

  private def handle_Delete(objectType: ContainerType, objectID: ObjectID, sourceUpdated: Boolean): Future[Boolean] = {
    for {
      result <- if (!sourceUpdated) {
        source.deleteObject(objectType, objectID)
      } else {
        Future.successful(true)
      }
      _ <- if (result) {
        self ? CacheStore.DeleteState(objectType, objectID)
      } else Future.successful(Unit)
    } yield {
      result
    }
  }

  private def handle_GenericQuery(objectsType: ContainerType): Future[Vector[Container]] = {
    (self ? CacheStore.GetAllContainers(objectsType)).mapTo[Vector[Container]]
  }

  private def handle_CustomQuery(
    objectsType: ContainerType,
    customQueryName: String,
    queryParams: Map[String, String]
  ): Future[Vector[Container]] = {
    val companion = containerDefinitions(objectsType)
    (self ? CacheStore.GetAllContainers(objectsType)).mapTo[Vector[Container]].map {
      containers =>
        containers.filter {
          current =>
            companion.matchCustomQuery(customQueryName, queryParams, current)
        }
    }
  }
}

object CacheStore {

  private sealed trait RevisionState

  private object RevisionState {

    case object InSync extends RevisionState

    case object ContainerAhead extends RevisionState

    case object ContainerBehind extends RevisionState

    case object NotNeeded extends RevisionState

    case object OutOfSync extends RevisionState

    case object InvalidData extends RevisionState

  }

  private case class GetAllContainers(objectsType: ContainerType)

  private case class UpdateState(container: Container)

  private case class BulkUpdateState(objectsType: ContainerType, containers: Vector[Container])

  private case class DeleteState(objectType: ContainerType, objectID: ObjectID)

  /**
    * Creates a new config object for the [[core3.database.dals.memory.utils.CacheStore]] actor.
    *
    * @param source                    the source DAL to use for data retrieval and updates
    * @param definitions               all configured container definitions
    * @param containerTypeMaxCacheSize maximum cache size per container type
    * @param actionTimeout             the amount of time (in seconds) before an operation is considered as timed out
    * @param maxLoadAttempts           maximum number of times to attempt loading an object from the source
    * @param preload                   if set to true, the cache will attempt to load all data during initialization
    * @return the config object
    */
  def props(
    source: DatabaseAbstractionLayer,
    definitions: ContainerDefinitions[BasicContainerDefinition],
    containerTypeMaxCacheSize: Int,
    actionTimeout: Int,
    maxLoadAttempts: Int,
    preload: Boolean
  )(implicit ec: ExecutionContext): Props = Props(
    classOf[CacheStore], source, definitions, containerTypeMaxCacheSize, actionTimeout, maxLoadAttempts, preload, ec
  )

  /**
    * Message instructing the Store to load a specific object from the database.
    *
    * Responds with: Nothing
    *
    * Notes:
    *   - Used for loading containers when database updates are performed outside the local system.
    *
    * @param objectType             the type of object to load
    * @param objectID               the ID of the object to load
    * @param revisionID             the revision ID of the object to load
    * @param revisionSequenceNumber the revision sequence number to load
    * @param attemptNumber          the current attempt number; by default starts from 0
    */
  case class LoadObject(
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID],
    revisionSequenceNumber: Option[RevisionSequenceNumber],
    attemptNumber: Int = 0
  )

  /**
    * Message instructing the Store to load objects from the database.
    *
    * Responds with: Future[Unit]
    *
    * @param objectsTypeOpt the type of objects to load; set to None to load all supported (default is None)
    */
  case class Load(objectsTypeOpt: Option[ContainerType] = None)

  /**
    * Message instructing the Store to clear the cache for the specified containers type.
    *
    * Responds with: Future[Unit]
    *
    * @param objectType the type of objects to clear
    */
  case class Clear(objectType: ContainerType)

  /**
    * Message instructing the Store to get a container.
    *
    * Responds with: Future[Container]
    *
    * @param objectType the requested container type
    * @param objectID   the requested object ID
    */
  case class Get(objectType: ContainerType, objectID: ObjectID)

  /**
    * Message instructing the Store to create the supplied container.
    *
    * Responds with: Future[Boolean]
    *
    * @param container the container to be created
    */
  case class Create(container: Container)

  /**
    * Message instructing the Store to do an update for the supplied container.
    *
    * Responds with: Future[Boolean]
    *
    * @param container the container to be updated
    */
  case class Update(container: MutableContainer)

  /**
    * Message instructing the Store to delete a container.
    *
    * Responds with: Future[Boolean]
    *
    * @param objectType    the type of container to be deleted
    * @param objectID      the ID of the container to be deleted
    * @param sourceUpdated set to true, if the source DAL has already been updated and
    *                      another deletion request should not be sent (default is false)
    */
  case class Delete(objectType: ContainerType, objectID: ObjectID, sourceUpdated: Boolean = false)

  /**
    * Message instructing the Store to run a generic query.
    *
    * Responds with: Future[ContainerSet]
    *
    * @param objectsType the container type to query
    */
  case class GenericQuery(objectsType: ContainerType)


  /**
    * Message instructing the Store to run a custom query with the specified parameters.
    *
    * Responds with: Future[ContainerSet]
    *
    * @param objectsType     the container type to query
    * @param customQueryName the name of the custom query
    * @param queryParams     the parameters to use for the query
    */
  case class CustomQuery(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String])

}
