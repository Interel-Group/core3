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
package core3.test.fixtures

import akka.actor.ActorRef
import akka.pattern.ask
import core3.config.StaticConfig
import core3.database._
import core3.database.containers._
import core3.database.dals._
import core3.database.dals.json.{CouchDB, ElasticSearch, Redis, Solr}
import core3.database.dals.memory.{DistributedCache, MemoryOnlyDB}
import core3.database.dals.sql.MariaDB
import core3.test.fixtures.TestSystem._
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

object Database {
  val defaultJsonCompanions: Map[ContainerType, JSONContainerCompanion] = Map[ContainerType, JSONContainerCompanion](
    "Group" -> core.Group,
    "TransactionLog" -> core.TransactionLog,
    "LocalUser" -> core.LocalUser
  )

  val defaultSlickCompanions: Map[ContainerType, SlickContainerCompanion] = Map[ContainerType, SlickContainerCompanion](
    "Group" -> core.Group,
    "TransactionLog" -> core.TransactionLog,
    "LocalUser" -> core.LocalUser
  )

  val defaultSearchCompanions: Map[ContainerType, SearchContainerCompanion] = Map[ContainerType, SearchContainerCompanion]()

  val defaultSupportedContainers: Vector[String] = Vector("Group", "TransactionLog", "LocalUser")

  def createCouchDBInstance(companions: Map[ContainerType, JSONContainerCompanion] = defaultJsonCompanions): DatabaseAbstractionLayer = {
    if (!JSONConverter.isInitialized) JSONConverter.initialize(companions)
    val config = StaticConfig.get.getConfig("database.couchdb")

    val actor = system.actorOf(
      CouchDB.props(
        config.getString("hostname"),
        config.getInt("port"),
        config.getString("schema"),
        config.getString("username"),
        config.getString("password"),
        companions,
        AhcWSClient()
      ),
      name = s"CouchDB_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createMariaDBInstance(companions: Map[ContainerType, SlickContainerCompanion] = defaultSlickCompanions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      MariaDB.props(
        companions,
        StaticConfig.get.getConfig("database.mariadb")
      ),
      name = s"MariaDB_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createSolrInstance(companions: Map[ContainerType, SearchContainerCompanion] = defaultSearchCompanions): DatabaseAbstractionLayer = {
    if (!JSONConverter.isInitialized) JSONConverter.initialize(companions)
    val actor = system.actorOf(
      Solr.props(containerCompanions = companions, AhcWSClient()),
      name = s"Solr_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createMemoryOnlyDBInstance(supportedContainers: Vector[String] = defaultSupportedContainers): DatabaseAbstractionLayer = {
    val actor = system.actorOf(MemoryOnlyDB.props(supportedContainers), name = s"MemoryOnlyDB_$getNewActorID")
    new DatabaseAbstractionLayer(actor)
  }

  def createRedisInstance(companions: Map[ContainerType, JSONContainerCompanion] = defaultJsonCompanions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      Redis.props(companions),
      name = s"Redis_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createElasticSearchInstance(
    coexist: Boolean = false,
    companions: Map[ContainerType, JSONContainerCompanion] = defaultSearchCompanions
  ): DatabaseAbstractionLayer = {
    if (!JSONConverter.isInitialized) JSONConverter.initialize(companions)
    val config = StaticConfig.get.getConfig("database.elastic-search")

    val actor = system.actorOf(
      ElasticSearch.props(
        config.getString("hostname"),
        config.getInt("port"),
        config.getString("clusterName"),
        companions,
        searchOnly = true,
        coexist,
        RefreshPolicy.NONE
      ),
      name = s"ElasticSearch_searchOnly_${if (coexist) "coexist" else "no_coexist"}_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createElasticStoreInstance(companions: Map[ContainerType, JSONContainerCompanion] = defaultJsonCompanions): DatabaseAbstractionLayer = {
    if (!JSONConverter.isInitialized) JSONConverter.initialize(companions)
    val config = StaticConfig.get.getConfig("database.elastic-store")

    val actor = system.actorOf(
      ElasticSearch.props(companions, config),
      name = s"ElasticSearch_no_searchOnly_no_coexist_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createDistributedCacheInstance(
    source: DatabaseAbstractionLayer = createMariaDBInstance(),
    companions: Map[ContainerType, BasicContainerCompanion] = defaultJsonCompanions,
    clusterPort: Int = 0,
    localPort: Int = 0,
    preload: Boolean = true
  ): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      DistributedCache.props(
        clusterHost = "127.0.0.1",
        clusterPort,
        localPort,
        source,
        preload,
        actionTimeout = 5,
        companions,
        1000,
        syncInterval = 5
      ),
      name = s"DistributedCache_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createCoreInstance(dals: Option[Map[ContainerType, Vector[ActorRef]]] = None, wipeData: Boolean = true): DatabaseAbstractionLayer = {
    val coreProps =
      if (dals.isDefined) {
        Core.props(dals.get)
      } else {
        val mariaDAL = createMariaDBInstance()
        val couchDAL = createCouchDBInstance()
        val memoryDAL = createMemoryOnlyDBInstance()
        val redisDAL = createRedisInstance()
        val elasticStoreDAL = createElasticStoreInstance()
        Core.props(
          Map(
            "TransactionLog" -> Vector(mariaDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef, elasticStoreDAL.getRef),
            "Group" -> Vector(mariaDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef, elasticStoreDAL.getRef),
            "LocalUser" -> Vector(mariaDAL.getRef, redisDAL.getRef, memoryDAL.getRef, couchDAL.getRef, elasticStoreDAL.getRef)
          )
        )
      }

    val actor = system.actorOf(coreProps, name = s"Core_${if (dals.isDefined) "custom" else "default"}_$getNewActorID")

    if (wipeData) {
      import Core._
      Await.result(
        for {
          _ <- actor ? ClearAllDatabases(ignoreErrors = true)
          _ <- actor ? BuildAllDatabases()
          _ <- actor ? VerifyAllDatabases()
        } yield {
          true
        },
        atMost = 15.seconds
      )
    }

    new DatabaseAbstractionLayer(actor)
  }
}
