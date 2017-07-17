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
package core3.test.specs.unit.http.controllers.local

import core3.config.StaticConfig
import core3.database.dals.DatabaseAbstractionLayer
import core3.http.controllers.local.ServiceController
import core3.http.responses.GenericResult
import play.api.Environment
import play.api.cache.SyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

class TestServiceController(db: DatabaseAbstractionLayer, cache: SyncCacheApi)(implicit environment: Environment, ec: ExecutionContext)
  extends ServiceController(cache, StaticConfig.get.getConfig("security.authentication.clients.LocalServiceController"), db) {
  def publicAction = PublicAction(
    okHandler = { (_, _) =>
      Future.successful(Ok(GenericResult(wasSuccessful = true, message = Some("Done")).asJson))
    }
  )

  def clientAwareAction = ClientAwareAction(
    requiredScope = "test-scope",
    okHandler = { (_, _) =>
      Future.successful(Ok(GenericResult(wasSuccessful = true, message = Some("Done")).asJson))
    }
  )

  def userAwareAction = UserAwareAction(
    requiredScope = "test-scope",
    okHandler = { (_, _) =>
      Future.successful(Ok(GenericResult(wasSuccessful = true, message = Some("Done")).asJson))
    }
  )
}
