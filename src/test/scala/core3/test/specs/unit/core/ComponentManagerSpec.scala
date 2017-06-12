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

import core3.config.RuntimeConfig
import core3.core.{ComponentManager, ComponentManagerActor}
import core3.test.fixtures
import core3.test.fixtures.TestSystem
import core3.test.specs.unit.AsyncUnitSpec

class ComponentManagerSpec extends AsyncUnitSpec {

  case class FixtureParam(manager: ComponentManager)

  def withFixture(test: OneArgAsyncTest) = {
    val coreDAL = fixtures.Database.createCoreInstance()

    val manager = new ComponentManager(
      TestSystem.system.actorOf(
        ComponentManagerActor.props(
          Map(
            "db" -> coreDAL.getRef
          )
        )(TestSystem.ec, TestSystem.timeout)
      )
    )(TestSystem.ec, TestSystem.timeout)

    val fixture = FixtureParam(manager)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  "A ComponentManager" should "successfully manage all configured components and the system" in {
    fixture =>
      //resets the modes
      RuntimeConfig.maintenance = false
      RuntimeConfig.metrics = false
      RuntimeConfig.trace = false

      for {
        actionResult <- fixture.manager.executeComponentAction("db", "stats")
        staticConfigResult <- fixture.manager.getStaticConfig
        dynamicConfigResult <- fixture.manager.getDynamicConfig
        reloadDynamicConfigResult <- fixture.manager.reloadDynamicConfig()
        modesResult <- fixture.manager.getModes
        setModeResult1 <- fixture.manager.setMode(ComponentManagerActor.SystemMode.Maintenance, enabled = true)
        getModeResult1 <- fixture.manager.getMode(ComponentManagerActor.SystemMode.Maintenance)
        getModeResult2 <- fixture.manager.getMode(ComponentManagerActor.SystemMode.Metrics)
        getModeResult3 <- fixture.manager.getMode(ComponentManagerActor.SystemMode.Trace)
        setModeResult2 <- fixture.manager.setMode(ComponentManagerActor.SystemMode.Metrics, enabled = true)
        setModeResult3 <- fixture.manager.setMode(ComponentManagerActor.SystemMode.Trace, enabled = true)
        updatedModesResult <- fixture.manager.getModes
        componentsResult <- fixture.manager.getComponents
      } yield {
        actionResult.wasSuccessful should be(true)
        actionResult.data should not be None
        actionResult.message should be(None)

        staticConfigResult.wasSuccessful should be(true)
        staticConfigResult.data should not be None
        staticConfigResult.message should be(None)

        dynamicConfigResult.wasSuccessful should be(true)
        dynamicConfigResult.data should not be None
        dynamicConfigResult.message should be(None)

        reloadDynamicConfigResult.wasSuccessful should be(true)
        reloadDynamicConfigResult.data should be(None)
        reloadDynamicConfigResult.message should be(None)

        modesResult.wasSuccessful should be(true)
        modesResult.data should not be None
        modesResult.message should be(None)
        (modesResult.data.get \ "Maintenance" \ "state").as[Boolean] should be(false)
        (modesResult.data.get \ "Trace" \ "state").as[Boolean] should be(false)
        (modesResult.data.get \ "Metrics" \ "state").as[Boolean] should be(false)

        setModeResult1.wasSuccessful should be(true)
        setModeResult1.data should be(None)
        setModeResult1.message should be(None)

        getModeResult1 should be(true)
        getModeResult2 should be(false)
        getModeResult3 should be(false)

        setModeResult2.wasSuccessful should be(true)
        setModeResult2.data should be(None)
        setModeResult2.message should be(None)

        setModeResult3.wasSuccessful should be(true)
        setModeResult3.data should be(None)
        setModeResult3.message should be(None)

        updatedModesResult.wasSuccessful should be(true)
        updatedModesResult.data should not be None
        updatedModesResult.message should be(None)
        (updatedModesResult.data.get \ "Maintenance" \ "state").as[Boolean] should be(true)
        (updatedModesResult.data.get \ "Trace" \ "state").as[Boolean] should be(true)
        (updatedModesResult.data.get \ "Metrics" \ "state").as[Boolean] should be(true)

        componentsResult.wasSuccessful should be(true)
        componentsResult.data should not be None
        componentsResult.message should be(None)
        componentsResult.data.get.as[Vector[String]] should contain("db")
      }
  }
}
