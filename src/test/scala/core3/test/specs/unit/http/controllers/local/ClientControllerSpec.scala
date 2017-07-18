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

import java.security.SecureRandom

import core3.config.StaticConfig
import core3.database.containers.core.LocalUser
import core3.http.responses.GenericResult
import core3.test.specs.unit.UnitSpec
import core3.test.fixtures.Database
import core3.test.fixtures.TestSystem
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Environment
import play.api.test.Helpers._
import play.api.test._
import play.api.cache.SyncCacheApi
import play.api.http.{HeaderNames, MimeTypes}

import scala.concurrent.duration._

class ClientControllerSpec extends UnitSpec with WsScalaTestClient with GuiceOneAppPerSuite {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  implicit private val waitDuration = 15.seconds
  implicit private val ec = TestSystem.ec
  implicit private val env = fakeApplication().injector.instanceOf[Environment]
  implicit private val db = Database.createCoreInstance()
  implicit private val authConfig = StaticConfig.get.getConfig("security.authentication.clients.LocalServiceController")
  implicit private val random = new SecureRandom()

  private val cache = fakeApplication().injector.instanceOf[SyncCacheApi]
  private val controller = new TestClientController(db, cache)
  controller.setControllerComponents(stubControllerComponents())

  createUser("test-admin", "some-test-password!", Vector("test-group"), LocalUser.UserType.Client)
  createUser("test-user", "some-test-password@", Vector.empty, LocalUser.UserType.Client)
  createUser("test-client", "some-test-password#", Vector.empty, LocalUser.UserType.Service)

  "A local ClientController" should "successfully handle public actions" in {
    _ =>
      val rawResult = controller.publicAction().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      )
      val result = contentAsJson(rawResult).as[GenericResult]

      result.wasSuccessful should be(true)
      result.data should be(None)
      result.message should not be None
      result.message.get should be("Done")
  }

  it should "allow users to log in and out" in {
    _ =>
      val authData = encodeCredentials("test-admin", "some-test-password!")

      val loginRawResult1 = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )
      val loginResult1 = contentAsJson(loginRawResult1).as[GenericResult]

      loginResult1.wasSuccessful should be(true)
      loginResult1.data should be(None)
      loginResult1.message should not be None
      loginResult1.message.get should be("Done")

      val loginRawResult2 = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )
      val loginResult2 = contentAsJson(loginRawResult2).as[GenericResult]

      loginResult2.wasSuccessful should be(true)
      loginResult2.data should be(None)
      loginResult2.message should not be None
      loginResult2.message.get should be("Done")

      val logoutRawResult = controller.logoutAction().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
          .withSession(("sessionToken", session(loginRawResult2).get("sessionToken").getOrElse("None")))
      )
      val logoutResult = contentAsString(logoutRawResult)

      logoutResult should be("")
  }

  it should "allow authenticated and authorized users to access actions" in {
    _ =>
      val authData = encodeCredentials("test-admin", "some-test-password!")

      val loginRawResult = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )

      val sessionToken = session(loginRawResult).get("sessionToken").getOrElse("None")

      val authorizedActionRawResult = controller.authorizedAction1().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
          .withSession(("sessionToken", sessionToken))
      )
      val authorizedActionResult = contentAsJson(authorizedActionRawResult).as[GenericResult]

      authorizedActionResult.wasSuccessful should be(true)
      authorizedActionResult.data should be(None)
      authorizedActionResult.message should not be None
      authorizedActionResult.message.get should be("Done")
  }

  it should "allow authenticated and authorized users to access actions via override config" in {
    _ =>
      val authorizedActionRawResult = controller.authorizedAction1().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      )
      val authorizedActionResult = contentAsJson(authorizedActionRawResult).as[GenericResult]

      authorizedActionResult.wasSuccessful should be(true)
      authorizedActionResult.data should be(None)
      authorizedActionResult.message should not be None
      authorizedActionResult.message.get should be("Done")
  }

  it should "fail authentication when invalid credentials are supplied" in {
    _ =>
      val authData1 = encodeCredentials("test-admin", "some-invalid-password!")
      val authData2 = encodeCredentials("invalid-user", "some-test-password!")

      val loginRawResult1 = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData1",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )
      val loginResult1 = contentAsJson(loginRawResult1).as[GenericResult]

      loginResult1.wasSuccessful should be(false)
      loginResult1.data should be(None)
      loginResult1.message should not be None
      loginResult1.message.get should be("Authentication Failed")

      val loginRawResult2 = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData2",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )
      val loginResult2 = contentAsJson(loginRawResult2).as[GenericResult]

      loginResult2.wasSuccessful should be(false)
      loginResult2.data should be(None)
      loginResult2.message should not be None
      loginResult2.message.get should be("Authentication Failed")

      val loginRawResult3 = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      )
      val loginResult3 = contentAsJson(loginRawResult3).as[GenericResult]

      loginResult3.wasSuccessful should be(false)
      loginResult3.data should be(None)
      loginResult3.message should not be None
      loginResult3.message.get should be("Login Required")
  }

  it should "fail authentication when unexpected users attempt to login" in {
    _ =>
      val authData = encodeCredentials("test-client", "some-test-password#")

      val loginRawResult = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )
      val loginResult = contentAsJson(loginRawResult).as[GenericResult]

      loginResult.wasSuccessful should be(false)
      loginResult.data should be(None)
      loginResult.message should not be None
      loginResult.message.get should be("Authentication Failed")
  }

  it should "fail to allow access to unauthorized users" in {
    _ =>
      val authData = encodeCredentials("test-user", "some-test-password@")

      val loginRawResult = controller.loginAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
      )

      val sessionToken = session(loginRawResult).get("sessionToken").getOrElse("None")

      val authorizedActionRawResult1 = controller.authorizedAction1().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
          .withSession(("sessionToken", sessionToken))
      )
      val authorizedActionResult1 = contentAsJson(authorizedActionRawResult1).as[GenericResult]

      authorizedActionResult1.wasSuccessful should be(false)
      authorizedActionResult1.data should be(None)
      authorizedActionResult1.message should not be None
      authorizedActionResult1.message.get should be("403 - Not authorized to access resource")

      val authorizedActionRawResult2 = controller.authorizedAction2().apply(
        FakeRequest()
          .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      )
      val authorizedActionResult2 = contentAsJson(authorizedActionRawResult2).as[GenericResult]

      authorizedActionResult2.wasSuccessful should be(false)
      authorizedActionResult2.data should be(None)
      authorizedActionResult2.message should not be None
      authorizedActionResult2.message.get should be("403 - Not authorized to access resource")
  }
}
