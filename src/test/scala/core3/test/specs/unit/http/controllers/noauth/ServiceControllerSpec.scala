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

class ServiceControllerSpec extends UnitSpec with WsScalaTestClient {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  "A no-auth ServiceController" should "successfully handle public actions" in {
    _ =>
      val controller = new TestServiceController()
      controller.setControllerComponents(stubControllerComponents())

      val rawResult = controller.publicAction().apply(FakeRequest())
      val result = contentAsString(rawResult)

      result should be("Done")
  }

  it should "fail to process actions that require authentication" in {
    _ =>
      val controller = new TestServiceController()
      controller.setControllerComponents(stubControllerComponents())

      val clientAwareActionRawResult = controller.clientAwareAction().apply(FakeRequest())
      val clientAwareActionResult = contentAsJson(clientAwareActionRawResult).as[GenericResult]

      clientAwareActionResult.wasSuccessful should be(false)
      clientAwareActionResult.data should be(None)
      clientAwareActionResult.message should not be None
      clientAwareActionResult.message.get should be("500 - Internal server error")

      val userAwareActionRawResult = controller.userAwareAction().apply(FakeRequest())
      val userAwareActionResult = contentAsJson(userAwareActionRawResult).as[GenericResult]

      userAwareActionResult.wasSuccessful should be(false)
      userAwareActionResult.data should be(None)
      userAwareActionResult.message should not be None
      userAwareActionResult.message.get should be("500 - Internal server error")
  }
}
