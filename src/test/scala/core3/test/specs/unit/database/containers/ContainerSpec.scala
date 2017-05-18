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
import core3.database.containers.{JsonContainerCompanion, SlickContainerCompanion}
import core3.test.fixtures.Database
import core3.test.specs.unit.AsyncUnitSpec
import scala.concurrent.duration._

class ContainerSpec extends AsyncUnitSpec with ContainerBehaviours {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest) = {
    val fixture = FixtureParam()
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  val jsonCompanions: Map[ContainerType, JsonContainerCompanion] = Map[ContainerType, JsonContainerCompanion](
    "QueryableContainer" -> QueryableContainer,
    "LargeContainer" -> LargeContainer
  )

  val slickCompanions: Map[ContainerType, SlickContainerCompanion] = Map[ContainerType, SlickContainerCompanion](
    "QueryableContainer" -> QueryableContainer,
    "LargeContainer" -> LargeContainer
  )

  val supportedContainers = Vector("QueryableContainer", "LargeContainer")

  implicit val timeout = Timeout(15.seconds)

  private val mariaDAL = Database.createMariaDBInstance(slickCompanions)
  private val couchDAL = Database.createCouchDBInstance(jsonCompanions)
  private val memoryDAL = Database.createMemoryOnlyDBInstance(supportedContainers)
  private val redisDAL = Database.createRedisInstance(jsonCompanions)
  private val elasticStoreDAL = Database.createElasticStoreInstance(jsonCompanions)

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
