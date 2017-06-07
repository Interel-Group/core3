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
package core3.test.specs.unit.mail

import akka.actor.ActorRef
import akka.pattern.ask
import core3.mail.Service
import core3.test.fixtures.TestSystem
import core3.test.specs.unit.AsyncUnitSpec
import core3.test.utils._
import org.jvnet.mock_javamail.Mailbox

class ServiceSpec extends AsyncUnitSpec {
  implicit private val ec = TestSystem.ec
  implicit private val system = TestSystem.system
  implicit private val timeout = TestSystem.timeout

  case class FixtureParam(srv: ActorRef)

  def withFixture(test: OneArgAsyncTest) = {
    val srv = system.actorOf(
      Service.props("localhost", 25, "test", "password"),
      name = s"mail_Service_${TestSystem.getNewActorID}"
    )

    val fixture = FixtureParam(srv)
    withFixture(test.toNoArgAsyncTest(fixture))
  }

  import Service._

  "A mail Service" should "successfully send emails" in {
    fixture =>
      val testSource = "test_source@example.com"
      val testTarget1 = "test_target1@example.com"
      val testTarget2 = "test_target2@example.com"
      val testTarget3 = "test_target3@example.com"
      val testSubject = "TEST EMAIL"
      val testBody = "<p>Test</p>"

      for {
        _ <- fixture.srv ? SendMessage(testSource, Vector(testTarget1), testSubject, testBody)
        _ <- fixture.srv ? SendMessage(testSource, Vector(testTarget1, testTarget2, testTarget3), testSubject, testBody, Vector.empty, Vector.empty)
      } yield {
        val targetMailbox1 = Mailbox.get(testTarget1)
        val targetMailbox2 = Mailbox.get(testTarget2)
        val targetMailbox3 = Mailbox.get(testTarget3)

        targetMailbox1 should have size 2
        targetMailbox2 should have size 1
        targetMailbox3 should have size 1

        val message1 = targetMailbox1.get(0)
        val message2 = targetMailbox1.get(1)
        val message3 = targetMailbox2.get(0)
        val message4 = targetMailbox3.get(0)

        message1.getSubject should equal(testSubject)
        message2.getSubject should equal(testSubject)
        message3.getSubject should equal(testSubject)
        message4.getSubject should equal(testSubject)
      }
  }

  it should "fail to send emails when invalid parameters are supplied" in {
    fixture =>
      val testSource = "test_source@example.com"
      val testTarget1 = "test_target@example.com"
      val testTarget2 = "test_target@example.com"
      val testTarget3 = "test_target@example.com"
      val testSubject = "TEST EMAIL"
      val testBody = "<p>Test</p>"

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage("", Vector(testTarget1), testSubject, testBody)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(""), testSubject, testBody)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(testTarget1), "", testBody)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(testTarget1), testSubject, "")).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage("", Vector(testTarget1, testTarget2, testTarget3), testSubject, testBody, Vector.empty, Vector.empty)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(), testSubject, testBody, Vector.empty, Vector.empty)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(testTarget1, testTarget2, testTarget3), "", testBody, Vector.empty, Vector.empty)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(testTarget1, testTarget2, testTarget3), testSubject, "", Vector.empty, Vector.empty)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector(""), testSubject, testBody, Vector.empty, Vector.empty)).await(printTrace = false)
      }

      assertThrows[IllegalArgumentException] {
        (fixture.srv ? SendMessage(testSource, Vector("", ""), testSubject, testBody, Vector.empty, Vector.empty)).await(printTrace = false)
      }

      val targetMailbox1 = Mailbox.get(testTarget1)
      val targetMailbox2 = Mailbox.get(testTarget2)
      val targetMailbox3 = Mailbox.get(testTarget3)

      targetMailbox1 should have size 0
      targetMailbox2 should have size 0
      targetMailbox3 should have size 0
  }
}
