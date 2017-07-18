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

class ServiceControllerSpec extends UnitSpec with WsScalaTestClient with GuiceOneAppPerSuite {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  implicit private val waitDuration = 15.seconds
  implicit private val ec = TestSystem.ec
  implicit private val env = fakeApplication().injector.instanceOf[Environment]
  implicit private val db = Database.createCoreInstance()
  implicit private val authConfig = StaticConfig.get.getConfig("security.authentication.clients.LocalServiceController")
  implicit private val random = new SecureRandom()

  private val cache = fakeApplication().injector.instanceOf[SyncCacheApi]
  private val controller = new TestServiceController(db, cache)
  controller.setControllerComponents(stubControllerComponents())

  createUser("test-admin", "some-test-password!", Vector.empty, LocalUser.UserType.Client)
  createUser("test-user", "some-test-password!", Vector.empty, LocalUser.UserType.Client)
  createUser("test-client", "some-test-password#", Vector("test-scope"), LocalUser.UserType.Service)
  createUser("other-client", "some-test-password!", Vector("other-scope"), LocalUser.UserType.Service)

  "A local ServiceController" should "successfully handle public actions" in {
    _ =>
      val rawResult = controller.publicAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val result = contentAsJson(rawResult).as[GenericResult]

      result.wasSuccessful should be(true)
      result.data should be(None)
      result.message should not be None
      result.message.get should be("Done")

      val withUserRawResult = controller.publicAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "test-admin",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val withUserResult = contentAsJson(withUserRawResult).as[GenericResult]

      withUserResult.wasSuccessful should be(true)
      withUserResult.data should be(None)
      withUserResult.message should not be None
      withUserResult.message.get should be("Done")
  }

  it should "allow authenticated and authorized users to access actions" in {
    _ =>
      val authData = encodeCredentials("test-client", "some-test-password#")

      val clientAwareRawResult = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult = contentAsJson(clientAwareRawResult).as[GenericResult]

      clientAwareResult.wasSuccessful should be(true)
      clientAwareResult.data should be(None)
      clientAwareResult.message should not be None
      clientAwareResult.message.get should be("Done")

      val clientAwareWithSessionRawResult = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN, clientAwareRawResult).getOrElse("None"),
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareWithSessionResult = contentAsJson(clientAwareWithSessionRawResult).as[GenericResult]

      clientAwareWithSessionResult.wasSuccessful should be(true)
      clientAwareWithSessionResult.data should be(None)
      clientAwareWithSessionResult.message should not be None
      clientAwareWithSessionResult.message.get should be("Done")

      val userAwareRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult = contentAsJson(userAwareRawResult).as[GenericResult]

      userAwareResult.wasSuccessful should be(true)
      userAwareResult.data should be(None)
      userAwareResult.message should not be None
      userAwareResult.message.get should be("Done")

      val userAwareWithSessionRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN, clientAwareRawResult).getOrElse("None"),
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareWithSessionResult = contentAsJson(userAwareWithSessionRawResult).as[GenericResult]

      userAwareWithSessionResult.wasSuccessful should be(true)
      userAwareWithSessionResult.data should be(None)
      userAwareWithSessionResult.message should not be None
      userAwareWithSessionResult.message.get should be("Done")

      val userAwareWithSessionAndUncachedUserRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN, clientAwareRawResult).getOrElse("None"),
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareWithSessionAndUncachedUserResult = contentAsJson(userAwareWithSessionAndUncachedUserRawResult).as[GenericResult]

      userAwareWithSessionAndUncachedUserResult.wasSuccessful should be(true)
      userAwareWithSessionAndUncachedUserResult.data should be(None)
      userAwareWithSessionAndUncachedUserResult.message should not be None
      userAwareWithSessionAndUncachedUserResult.message.get should be("Done")
  }

  it should "fail authentication when invalid credentials are supplied" in {
    _ =>
      val authData1 = encodeCredentials("test-client", "some-invalid-password#")
      val authData2 = encodeCredentials("test-user", "some-test-password!")

      val clientAwareRawResult1 = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData1",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult1 = contentAsJson(clientAwareRawResult1).as[GenericResult]

      clientAwareResult1.wasSuccessful should be(false)
      clientAwareResult1.data should be(None)
      clientAwareResult1.message should not be None
      clientAwareResult1.message.get should be("401 - Not authenticated")

      val userAwareRawResult1 = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData1",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult1 = contentAsJson(userAwareRawResult1).as[GenericResult]

      userAwareResult1.wasSuccessful should be(false)
      userAwareResult1.data should be(None)
      userAwareResult1.message should not be None
      userAwareResult1.message.get should be("401 - Not authenticated")

      val clientAwareRawResult2 = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData2",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult2 = contentAsJson(clientAwareRawResult2).as[GenericResult]

      clientAwareResult2.wasSuccessful should be(false)
      clientAwareResult2.data should be(None)
      clientAwareResult2.message should not be None
      clientAwareResult2.message.get should be("401 - Not authenticated")

      val userAwareRawResult2 = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData2",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult2 = contentAsJson(userAwareRawResult2).as[GenericResult]

      userAwareResult2.wasSuccessful should be(false)
      userAwareResult2.data should be(None)
      userAwareResult2.message should not be None
      userAwareResult2.message.get should be("401 - Not authenticated")

      val clientAwareRawResult3 = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult3 = contentAsJson(clientAwareRawResult3).as[GenericResult]

      clientAwareResult3.wasSuccessful should be(false)
      clientAwareResult3.data should be(None)
      clientAwareResult3.message should not be None
      clientAwareResult3.message.get should be("401 - Not authenticated")

      val userAwareRawResult3 = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult3 = contentAsJson(userAwareRawResult3).as[GenericResult]

      userAwareResult3.wasSuccessful should be(false)
      userAwareResult3.data should be(None)
      userAwareResult3.message should not be None
      userAwareResult3.message.get should be("401 - Not authenticated")
  }

  it should "fail authentication when unexpected users attempt to login" in {
    _ =>
      val authData = encodeCredentials("test-admin", "some-test-password!")

      val clientAwareRawResult = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult = contentAsJson(clientAwareRawResult).as[GenericResult]

      clientAwareResult.wasSuccessful should be(false)
      clientAwareResult.data should be(None)
      clientAwareResult.message should not be None
      clientAwareResult.message.get should be("401 - Not authenticated")

      val userAwareRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult = contentAsJson(userAwareRawResult).as[GenericResult]

      userAwareResult.wasSuccessful should be(false)
      userAwareResult.data should be(None)
      userAwareResult.message should not be None
      userAwareResult.message.get should be("401 - Not authenticated")
  }

  it should "fail to allow access to unauthorized users" in {
    _ =>
      val authData = encodeCredentials("other-client", "some-test-password!")

      val clientAwareRawResult = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareResult = contentAsJson(clientAwareRawResult).as[GenericResult]

      clientAwareResult.wasSuccessful should be(false)
      clientAwareResult.data should be(None)
      clientAwareResult.message should not be None
      clientAwareResult.message.get should be("403 - Not authorized to access resource")

      val userAwareRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareResult = contentAsJson(userAwareRawResult).as[GenericResult]

      userAwareResult.wasSuccessful should be(false)
      userAwareResult.data should be(None)
      userAwareResult.message should not be None
      userAwareResult.message.get should be("403 - Not authorized to access resource")

      val clientAwareWithSessionRawResult = controller.clientAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN, clientAwareRawResult).getOrElse("None"),
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "None"
          )
      )
      val clientAwareWithSessionResult = contentAsJson(clientAwareWithSessionRawResult).as[GenericResult]

      clientAwareWithSessionResult.wasSuccessful should be(false)
      clientAwareWithSessionResult.data should be(None)
      clientAwareWithSessionResult.message should not be None
      clientAwareWithSessionResult.message.get should be("403 - Not authorized to access resource")

      val userAwareWithSessionRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> header(core3.http.HeaderNames.CLIENT_SESSION_TOKEN, userAwareRawResult).getOrElse("None"),
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "test-admin"
          )
      )
      val userAwareWithSessionResult = contentAsJson(userAwareWithSessionRawResult).as[GenericResult]

      userAwareWithSessionResult.wasSuccessful should be(false)
      userAwareWithSessionResult.data should be(None)
      userAwareWithSessionResult.message should not be None
      userAwareWithSessionResult.message.get should be("403 - Not authorized to access resource")
  }

  it should "fail to allow access when an invalid delegation user is supplied" in {
    _ =>
      val authData = encodeCredentials("test-client", "some-test-password#")

      val userAwareRawResult = controller.userAwareAction().apply(
        FakeRequest()
          .withHeaders(
            HeaderNames.AUTHORIZATION -> s"Basic $authData",
            HeaderNames.ACCEPT -> MimeTypes.JSON,
            core3.http.HeaderNames.CLIENT_SESSION_TOKEN -> "None",
            core3.http.HeaderNames.USER_DELEGATION_TOKEN -> "invalid-user"
          )
      )
      val userAwareResult = contentAsJson(userAwareRawResult).as[GenericResult]

      userAwareResult.wasSuccessful should be(false)
      userAwareResult.data should be(None)
      userAwareResult.message should not be None
      userAwareResult.message.get should be("401 - Not authenticated")
  }
}
