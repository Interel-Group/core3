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
package core3.test.specs.perf.memory.database.dals

import core3.config.StaticConfig
import org.scalameter.Bench
import org.scalameter.api._

object DALsGroup extends Bench.Group {
  private val resultDirConfigPath = "testing.reports.memory.dsvPath"
  private val resultDir = if (StaticConfig.get.hasPath(resultDirConfigPath)) {
    StaticConfig.get.getString(resultDirConfigPath)
  } else {
    "target/specs.perf/memory/dsv"
  }

  performance of "memory.core3.database.dals" config(
    reports.resultDir -> resultDir,
    exec.benchRuns -> 10,
    exec.independentSamples -> 4,
    exec.warmupCovThreshold -> 0.5
  ) in {
    include(new CoreSpec {})
    include(new MariaDBSpec {})
    include(new CouchDBSpec {})
    include(new MemoryOnlyDBSpec {})
    include(new RedisSpec {})
    include(new ElasticStoreSpec {})
    include(new DistributedCacheSpec {})
  }
}
