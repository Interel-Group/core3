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
package core3_multi_jvm.test.utils

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

/**
  * Class for initializing a cluster when no seed nodes are available.
  *
  * Notes:
  * - No messages will be processed by this node.
  * - The cluster is initialized only after a call to 'init'.
  *
  * @param clusterHost   the hostname to be used for the node/cluster
  * @param clusterPort   the port to be used for the node/cluster
  * @param systemName    the cluster system name
  * @param actorProvider the actor provider (default is 'akka.cluster.ClusterActorRefProvider')
  */
class ClusterInitNode(
  private val clusterHost: String,
  private val clusterPort: Int,
  private val systemName: String,
  private val actorProvider: String = "akka.cluster.ClusterActorRefProvider"
) {
  private val config = ConfigFactory.load()
    .withValue("akka.actor.provider", ConfigValueFactory.fromAnyRef(actorProvider))
    .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(clusterPort))
    .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(clusterHost))
  private val localSystem = ActorSystem(systemName, config)
  private val cluster = Cluster(localSystem)

  /**
    * Instructs the node to join the cluster at its own address.
    */
  def init(): Unit = {
    cluster.join(cluster.selfAddress)
  }

  /**
    * Instructs the node to leave the cluster.
    */
  def leave(): Unit = {
    cluster.leave(cluster.selfAddress)
  }

  /**
    * Retrieves the number of cluster nodes in state 'Up', excluding itself.
    *
    * @return the current number of nodes in state 'Up'
    */
  def activeNodesCount: Int = {
    cluster.state.members.count(_.status == MemberStatus.Up) - 1
  }
}
