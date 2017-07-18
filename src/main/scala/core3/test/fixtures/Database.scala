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
import core3.database.containers.core
import core3.database.dals._
import core3.database.dals.json.{CouchDB, ElasticSearch, Redis, Solr}
import core3.database.dals.memory.{DistributedCache, MemoryOnlyDB}
import core3.database.dals.jdbc.SlickDB
import core3.test.fixtures.TestSystem._
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import play.api.libs.ws.ahc.AhcWSClient
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._

object Database {
  val groupDefinitions = new core.Group.BasicDefinition with core.Group.JsonDefinition with core.Group.SlickDefinition {
    override protected def withProfile: JdbcProfile = slick.jdbc.MySQLProfile
  }

  val transactionLogDefinitions = new core.TransactionLog.BasicDefinition with core.TransactionLog.JsonDefinition with core.TransactionLog.SlickDefinition {
    override protected def withProfile: JdbcProfile = slick.jdbc.MySQLProfile
  }

  val localUserDefinitions = new core.LocalUser.BasicDefinition with core.LocalUser.JsonDefinition with core.LocalUser.SlickDefinition {
    override protected def withProfile: JdbcProfile = slick.jdbc.MySQLProfile
  }

  val defaultDefinitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition with SlickContainerDefinition] =
    Map(
      "Group" -> groupDefinitions,
      "TransactionLog" -> transactionLogDefinitions,
      "LocalUser" -> localUserDefinitions
    )

  val defaultSearchDefinitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition with SearchContainerDefinition] = Map.empty

  def createCouchDBInstance(definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition] = defaultDefinitions): DatabaseAbstractionLayer = {
    val config = StaticConfig.get.getConfig("database.couchdb")

    val actor = system.actorOf(
      CouchDB.props(
        config.getString("hostname"),
        config.getInt("port"),
        config.getString("schema"),
        config.getString("username"),
        config.getString("password"),
        definitions,
        AhcWSClient()
      ),
      name = s"CouchDB_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createMariaDBInstance(definitions: Map[ContainerType, BasicContainerDefinition with SlickContainerDefinition] = defaultDefinitions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      SlickDB.props(
        definitions,
        slick.jdbc.MySQLProfile,
        StaticConfig.get.getConfig("database.mariadb")
      ),
      name = s"MariaDB_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createSolrInstance(definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition with SearchContainerDefinition] = defaultSearchDefinitions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      Solr.props(containerDefinitions = definitions, AhcWSClient()),
      name = s"Solr_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createMemoryOnlyDBInstance(definitions: Map[ContainerType, BasicContainerDefinition] = defaultDefinitions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(MemoryOnlyDB.props(definitions), name = s"MemoryOnlyDB_$getNewActorID")
    new DatabaseAbstractionLayer(actor)
  }

  def createRedisInstance(definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition] = defaultDefinitions): DatabaseAbstractionLayer = {
    val actor = system.actorOf(
      Redis.props(definitions),
      name = s"Redis_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createElasticSearchInstance(
    coexist: Boolean = false,
    definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition] = defaultSearchDefinitions
  ): DatabaseAbstractionLayer = {
    val config = StaticConfig.get.getConfig("database.elastic-search")

    val actor = system.actorOf(
      ElasticSearch.props(
        config.getString("hostname"),
        config.getInt("port"),
        config.getString("clusterName"),
        definitions,
        searchOnly = true,
        coexist,
        RefreshPolicy.NONE,
        scrollSize = 10,
        scrollExpiration = 30 //in seconds
      ),
      name = s"ElasticSearch_searchOnly_${if (coexist) "coexist" else "no_coexist"}_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createElasticStoreInstance(definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition] = defaultDefinitions): DatabaseAbstractionLayer = {
    val config = StaticConfig.get.getConfig("database.elastic-store")

    val actor = system.actorOf(
      ElasticSearch.props(definitions, config),
      name = s"ElasticSearch_no_searchOnly_no_coexist_$getNewActorID"
    )

    new DatabaseAbstractionLayer(actor)
  }

  def createDistributedCacheInstance(
    source: DatabaseAbstractionLayer = createMariaDBInstance(),
    definitions: Map[ContainerType, BasicContainerDefinition] = defaultDefinitions,
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
        definitions,
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
