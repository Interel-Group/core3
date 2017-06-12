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

import core3.http.responses.GenericResult
import core3.test.specs.unit.UnitSpec
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

class ClientControllerSpec extends UnitSpec with WsScalaTestClient {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  "A no-auth ClientController" should "successfully handle public actions" in {
    _ =>
      val controller = new TestClientController()

      val rawResult = controller.publicAction().apply(FakeRequest())
      val result = contentAsString(rawResult)

      result should be("Done")
  }

  it should "fail to process actions that require authentication" in {
    _ =>
      val controller = new TestClientController()

      val authorizedActionRawResult = controller.authorizedAction().apply(FakeRequest())
      val authorizedActionResult = contentAsJson(authorizedActionRawResult).as[GenericResult]

      authorizedActionResult.wasSuccessful should be(false)
      authorizedActionResult.data should be(None)
      authorizedActionResult.message should not be None
      authorizedActionResult.message.get should be("500 - Internal server error")

      val loginActionRawResult = controller.loginAction().apply(FakeRequest())
      val loginActionResult = contentAsJson(loginActionRawResult).as[GenericResult]

      loginActionResult.wasSuccessful should be(false)
      loginActionResult.data should be(None)
      loginActionResult.message should not be None
      loginActionResult.message.get should be("500 - Internal server error")

      val logoutActionRawResult = controller.logoutAction().apply(FakeRequest())
      val logoutActionResult = contentAsJson(logoutActionRawResult).as[GenericResult]

      logoutActionResult.wasSuccessful should be(false)
      logoutActionResult.data should be(None)
      logoutActionResult.message should not be None
      logoutActionResult.message.get should be("500 - Internal server error")
  }
}
