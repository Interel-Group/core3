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

import akka.actor.{Actor, ActorRef, Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}
import akka.cluster.{Cluster, MemberStatus}
import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Akka actor for notifying nodes of updates in a [[core3.database.dals.memory.DistributedCache]] cluster.
  * <br><br>
  * Notes:
  * <ul>
  * <li>TLS support for cluster communication is enabled via Netty config. Actual settings can be found in Akka's
  * Remoting docs (http://doc.akka.io/docs/akka/current/scala/remoting.html).</li>
  * <li>Message synchronization is performed by broadcasting a local node's last message ID. Should a remote node
  * find that it is behind that (message IDs are sequential), a full cache reload is triggered.</li>
  * </ul>
  *
  * @param cacheName      the cache name to be used for grouping messages
  * @param store          reference to the local cache store
  * @param syncStartDelay initial sync delay time (in ms)
  * @param syncInterval   message sync broadcast interval
  * @param ec             implicit execution context
  */
class ClusterMessenger(
  private val cacheName: String,
  private val store: ActorRef,
  private val syncStartDelay: Int,
  private val syncInterval: Int
)
  (implicit ec: ExecutionContext)
  extends Actor {

  import ClusterMessenger.{ClusterMessageID, ClusterSyncID}

  private val receivedMessages = mutable.HashMap.empty[Address, Long]
  private var lastMessageID: ClusterMessageID = 0L
  //overflows are acceptable as it may only cause a cache reload
  private val auditLogger = Logger("audit")

  //cluster setup
  private val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)

  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(cacheName, self)

  //sync setup
  private var lastSyncID: ClusterSyncID = 0L
  //overflows are acceptable as it may only cause a cache reload
  private val syncSchedule = context.system.scheduler.schedule(
    syncStartDelay.seconds,
    syncInterval.seconds,
    self,
    ClusterMessenger.SyncBroadcast()
  )
  auditLogger.info(s"core3.database.dals.memory.utils.ClusterMessenger::() > " +
    s"Scheduled message sync in [$syncStartDelay] seconds with interval of [$syncInterval] seconds.")

  private def handle_NotifyCluster(
    updateType: ClusterMessenger.UpdateType,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  ): Unit = {
    lastMessageID += 1

    updateType match {
      case ClusterMessenger.UpdateType.Create =>
        mediator ! Publish(
          cacheName,
          ClusterMessenger.Created(
            cluster.selfAddress,
            lastMessageID,
            objectType,
            objectID,
            revisionID,
            revisionSequenceNumber
          )
        )

      case ClusterMessenger.UpdateType.Update =>
        mediator ! Publish(
          cacheName,
          ClusterMessenger.Updated(
            cluster.selfAddress,
            lastMessageID,
            objectType,
            objectID,
            revisionID,
            revisionSequenceNumber
          )
        )

      case ClusterMessenger.UpdateType.Delete =>
        mediator ! Publish(
          cacheName,
          ClusterMessenger.Deleted(
            cluster.selfAddress,
            lastMessageID,
            objectType,
            objectID
          )
        )
    }
  }

  private def handle_Created(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  ): Unit = {
    receivedMessages(from) += 1
    store ! CacheStore.LoadObject(objectType, objectID, revisionID, revisionSequenceNumber)
  }

  private def handle_Updated(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  ): Unit = {
    receivedMessages(from) += 1
    store ! CacheStore.LoadObject(objectType, objectID, revisionID, revisionSequenceNumber)
  }

  private def handle_Deleted(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID
  ): Unit = {
    receivedMessages(from) += 1
    store ! CacheStore.Delete(objectType, objectID, sourceUpdated = true)
  }

  private def handle_SyncBroadcast(): Unit = {
    lastSyncID += 1
    mediator ! Publish(cacheName, ClusterMessenger.SyncRequested(cluster.selfAddress, lastSyncID, lastMessageID))
  }

  private def handle_SyncRequested(
    from: Address,
    syncID: ClusterSyncID,
    lastSentMessageID: ClusterMessageID
  ): Unit = {
    if (receivedMessages(from) == lastSentMessageID) {
      auditLogger.debug(s"core3.database.dals.memory.utils.ClusterMessenger::handle_SyncRequested > " +
        s"Sync broadcast [$syncID] from [$from]: messages in sync.")
    } else {
      auditLogger.warn(s"core3.database.dals.memory.utils.ClusterMessenger::handle_SyncRequested > " +
        s"Sync broadcast [$syncID] from [$from]: messages out of sync; " +
        s"[${receivedMessages(from)}] out of [$lastSentMessageID] messages received.")
      receivedMessages(from) = lastSentMessageID
      store ! CacheStore.Load(objectsTypeOpt = None)
    }
  }

  private def handle_StopMessenger(): Unit = {
    syncSchedule.cancel()
    mediator ! Unsubscribe(cacheName, self)
  }

  override def receive: Receive = {
    //Outgoing Cluster Updates
    case ClusterMessenger.NotifyCluster(updateType, objectType, objectID, revisionID, revisionSequenceNumber) =>
      handle_NotifyCluster(updateType, objectType, objectID, revisionID, revisionSequenceNumber)

    //Incoming Cluster Updates
    case ClusterMessenger.Created(from, messageID, objectType, objectID, revisionID, revisionSequenceNumber) =>
      if (sender != self) handle_Created(from, messageID, objectType, objectID, revisionID, revisionSequenceNumber)

    case ClusterMessenger.Updated(from, messageID, objectType, objectID, revisionID, revisionSequenceNumber) =>
      if (sender != self) handle_Updated(from, messageID, objectType, objectID, revisionID, revisionSequenceNumber)

    case ClusterMessenger.Deleted(from, messageID, objectType, objectID) =>
      if (sender != self) handle_Deleted(from, messageID, objectType, objectID)

    //Cluster Synchronization
    case ClusterMessenger.SyncBroadcast() =>
      handle_SyncBroadcast()

    case ClusterMessenger.SyncRequested(from, syncID, lastSentMessageID) =>
      if (sender != self) handle_SyncRequested(from, syncID, lastSentMessageID)

    //Cluster Monitoring
    case state: CurrentClusterState =>
      state.members.foreach {
        member =>
          if (member.status == MemberStatus.Up) receivedMessages += member.address -> 0L
      }

    case MemberUp(member) =>
      receivedMessages += member.address -> 0L

    case MemberRemoved(member, _) =>
      receivedMessages -= member.address

    //Messenger Life-Cycle
    case ClusterMessenger.StopMessenger() =>
      handle_StopMessenger()
      sender() ! Unit
  }
}

object ClusterMessenger {

  sealed trait UpdateType

  object UpdateType {

    case object Create extends UpdateType

    case object Update extends UpdateType

    case object Delete extends UpdateType

  }

  private type ClusterMessageID = Long
  private type ClusterSyncID = Long

  private case class Created(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  )

  private case class Updated(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  )

  private case class Deleted(
    from: Address,
    messageID: ClusterMessageID,
    objectType: ContainerType,
    objectID: ObjectID
  )

  private case class SyncBroadcast()

  private case class SyncRequested(from: Address, syncID: ClusterSyncID, lastSentMessageID: ClusterMessageID)

  /**
    * Creates a new config object for the [[core3.database.dals.memory.utils.ClusterMessenger]] actor.
    *
    * @param cacheName      the cache name to be used grouping messages
    * @param store          reference to the local cache store
    * @param syncStartDelay initial sync delay time (in ms)
    * @param syncInterval   message sync broadcast interval
    * @param ec             implicit execution context
    * @return the config object
    */
  def props(
    cacheName: String,
    store: ActorRef,
    syncStartDelay: Int,
    syncInterval: Int
  )(implicit ec: ExecutionContext): Props = Props(classOf[ClusterMessenger], cacheName, store, syncStartDelay, syncInterval, ec)

  /**
    * Message instructing the cluster messenger to notify the cluster of an update.
    *
    * Responds with: Nothing
    *
    * @param updateType             the type of update being made
    * @param objectType             the affected container type
    * @param objectID               the affected container ID
    * @param revisionID             the affected container revision ID (for mutable container updates only)
    * @param revisionSequenceNumber the affected container revision number (for mutable container updates only)
    */
  case class NotifyCluster(
    updateType: UpdateType,
    objectType: ContainerType,
    objectID: ObjectID,
    revisionID: Option[RevisionID] = None,
    revisionSequenceNumber: Option[RevisionSequenceNumber] = None
  )

  /**
    * Message instructing the cluster messenger to stop accepting and sending notifications.
    *
    * Responds with: Future[Unit]
    */
  case class StopMessenger()

}
