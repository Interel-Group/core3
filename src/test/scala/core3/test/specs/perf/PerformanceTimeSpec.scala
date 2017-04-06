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
package core3.test.specs.perf

import core3.config.StaticConfig
import org.scalameter.Reporter
import org.scalameter.api.Bench
import org.scalameter.persistence.GZIPJSONSerializationPersistor
import org.scalameter.reporting.{DsvReporter, LoggingReporter, RegressionReporter}

abstract class PerformanceTimeSpec extends Bench.LocalTime {
  override def persistor = new GZIPJSONSerializationPersistor(PerformanceTimeSpec.resultDir)

  override def reporter = Reporter.Composite(
    new RegressionReporter(
      RegressionReporter.Tester.Accepter(),
      RegressionReporter.Historian.Complete()),
    new DsvReporter(delimiter = ','),
    new LoggingReporter
  )
}

object PerformanceTimeSpec {
  val resultDirConfigPath = "testing.reports.time.jsonPath"
  val resultDir: String = if (StaticConfig.get.hasPath(resultDirConfigPath)) {
    StaticConfig.get.getString(resultDirConfigPath)
  } else {
    "target/specs.perf/time/json"
  }
}
