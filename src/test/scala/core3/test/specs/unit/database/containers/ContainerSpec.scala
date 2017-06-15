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
package core3.test.specs.unit.database.containers

import akka.util.Timeout
import core3.database._
import core3.database.containers.{BasicContainerDefinition, JsonContainerDefinition, SlickContainerDefinition}
import core3.test.fixtures.Database
import core3.test.specs.unit.AsyncUnitSpec
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._

class ContainerSpec extends AsyncUnitSpec with ContainerBehaviours {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest) = {
    val fixture = FixtureParam()
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  val queryableContainerDefinitions = new QueryableContainer.BasicDefinition with QueryableContainer.JsonDefinition with QueryableContainer.SlickDefinition with SlickContainerDefinition {
    override protected def withProfile: JdbcProfile = slick.jdbc.MySQLProfile
  }

  val largeContainerDefinitions = new LargeContainer.BasicDefinition with LargeContainer.JsonDefinition with LargeContainer.SlickDefinition with SlickContainerDefinition {
    override protected def withProfile: JdbcProfile = slick.jdbc.MySQLProfile
  }

  val definitions: Map[ContainerType, BasicContainerDefinition with JsonContainerDefinition with SlickContainerDefinition] = Map(
    "QueryableContainer" -> queryableContainerDefinitions,
    "LargeContainer" -> largeContainerDefinitions
  )

  implicit val timeout = Timeout(15.seconds)

  private val mariaDAL = Database.createMariaDBInstance(definitions)
  private val couchDAL = Database.createCouchDBInstance(definitions)
  private val memoryDAL = Database.createMemoryOnlyDBInstance(definitions)
  private val redisDAL = Database.createRedisInstance(definitions)
  private val elasticStoreDAL = Database.createElasticStoreInstance(definitions)

  val mariaDBMasterMap = Map(
    "QueryableContainer" -> Vector(mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef, redisDAL.getRef),
    "LargeContainer" -> Vector(mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef, redisDAL.getRef)
  )

  val couchDBMasterMap = Map(
    "QueryableContainer" -> Vector(couchDAL.getRef, memoryDAL.getRef, mariaDAL.getRef, redisDAL.getRef),
    "LargeContainer" -> Vector(couchDAL.getRef, memoryDAL.getRef, mariaDAL.getRef, redisDAL.getRef)
  )

  val redisMasterMap = Map(
    "QueryableContainer" -> Vector(redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "LargeContainer" -> Vector(redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef)
  )

  val elasticMasterMap = Map(
    "QueryableContainer" -> Vector(elasticStoreDAL.getRef, redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef),
    "LargeContainer" -> Vector(elasticStoreDAL.getRef, redisDAL.getRef, mariaDAL.getRef, memoryDAL.getRef, couchDAL.getRef)
  )

  "A Core with a MariaDB master database" should behave like
    standardCoreDAL(Database.createCoreInstance(Some(mariaDBMasterMap)))

  "A Core with a CouchDB master database" should behave like
    standardCoreDAL(Database.createCoreInstance(Some(couchDBMasterMap)))

  "A Core with a Redis master database" should behave like
    standardCoreDAL(Database.createCoreInstance(Some(redisMasterMap)))

  "A Core with a ElasticSearch master database" should behave like
    standardCoreDAL(Database.createCoreInstance(Some(elasticMasterMap)))
}
