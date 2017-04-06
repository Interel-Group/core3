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
package core3.config

import com.typesafe.config._

/**
  * Object for accessing global dynamic config.
  * <br><br>
  * Loaded from file: 'dynamic.conf'; dynamic config can be updated/reloaded at runtime.
  */
object DynamicConfig {
  private var config = ConfigFactory.load("dynamic").getConfig("server.dynamic")

  /**
    * Retrieves the current config.
    *
    * @return the current dynamic config
    */
  def get: Config = {
    config
  }

  /**
    * Reloads the config from the 'dynamic.conf' file.
    */
  def reload() = {
    config = ConfigFactory.load("dynamic").getConfig("server.dynamic")
  }
}
