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
package core3.test.specs.unit.http.controllers.noauth

import core3.http.controllers.noauth.ServiceController

import scala.concurrent.Future

class TestServiceController extends ServiceController {
  def publicAction = PublicAction(
    okHandler = { (_, _) =>
      Future.successful(Ok("Done"))
    }
  )

  def clientAwareAction = ClientAwareAction(
    requiredScope = "test-scope",
    okHandler = { (_, _) =>
      Future.successful(Ok("Done"))
    }
  )

  def userAwareAction = UserAwareAction(
    requiredScope = "test-scope",
    okHandler = { (_, _) =>
      Future.successful(Ok("Done"))
    }
  )
}
