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
package core3.test.specs.unit.core

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import core3.core.Component._
import core3.database.dals.LayerType
import core3.test.fixtures
import core3.test.specs.unit.AsyncUnitSpec

import scala.concurrent.duration._

class ComponentSpec extends AsyncUnitSpec {

  case class FixtureParam(component: ActorRef)

  def withFixture(test: OneArgAsyncTest) = {
    val memoryDAL = fixtures.Database.createMemoryOnlyDBInstance()

    val fixture = FixtureParam(memoryDAL.getRef)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  implicit val timeout = Timeout(15.seconds)

  "A Component" should "successfully execute actions and return results" in {
    fixture =>
      for {
        result <- (fixture.component ? ExecuteAction("stats")).mapTo[ActionResult]
      } yield {
        result.wasSuccessful should be(true)
        result.data should not be None
        result.message should be(None)
        (result.data.get \ "layerType").as[LayerType] should be(LayerType.MemoryOnlyDB)
      }
  }
}
