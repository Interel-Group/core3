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

import core3.http.controllers.noauth.ClientController

import scala.concurrent.Future

class TestClientController extends ClientController {
  def authorizedAction = AuthorizedAction(
    requiredGroup = "test-group",
    okHandler = { (_, _) =>
      Future.successful(Ok("Done"))
    }
  )

  def publicAction = PublicAction(
    okHandler = { (_, _) =>
      Future.successful(Ok("Done"))
    }
  )

  def loginAction = LoginAction(
    okHandler = { implicit request => //success
      Future.successful(Ok("Done"))
    },
    unauthorizedHandler = { implicit request => //not allowed
      Future.successful(Unauthorized("Authentication Failed"))
    },
    loginHandler = { implicit request => //should log in
      Future.successful(Unauthorized("Login Required"))
    }
  )

  def logoutAction = LogoutAction()
}
