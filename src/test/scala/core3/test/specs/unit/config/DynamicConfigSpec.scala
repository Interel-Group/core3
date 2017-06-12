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
package core3.test.specs.unit.config

import core3.test.specs.unit.UnitSpec
import core3.config.DynamicConfig

class DynamicConfigSpec extends UnitSpec {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  "A DynamicConfig object" should "successfully retrieve and reload the configuration" in {
    _ =>
      val config = DynamicConfig.get.getConfig("testing.config")

      config.getString("valueOne") should be("1")
      config.getDouble("valueTwo") should be(2.3)
      config.getBoolean("valueThree") should be(true)

      DynamicConfig.reload()
      val reloadedConfig = DynamicConfig.get.getConfig("testing.config")

      reloadedConfig.getString("valueOne") should be("1")
      reloadedConfig.getDouble("valueTwo") should be(2.3)
      reloadedConfig.getBoolean("valueThree") should be(true)
  }
}
