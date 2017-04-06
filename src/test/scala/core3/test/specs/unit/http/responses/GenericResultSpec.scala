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
package core3.test.specs.unit.http.responses

import core3.http.responses.GenericResult
import core3.test.specs.unit.UnitSpec
import play.api.libs.json.{JsNumber, JsObject, JsString, Json}

class GenericResultSpec extends UnitSpec {

  case class FixtureParam()

  def withFixture(test: OneArgTest) = withFixture(test.toNoArgTest(FixtureParam()))

  "A GenericResult" should "successfully combine with other results" in {
    _ =>
      val result1 = GenericResult(wasSuccessful = true)
      val result2 = GenericResult(wasSuccessful = false)
      val result3 = GenericResult(wasSuccessful = true, message = Some("Result #3"))
      val result4 = GenericResult(wasSuccessful = false, message = Some("Result #4"))
      val result5 = GenericResult(wasSuccessful = true, message = Some("Result #5"), data = Some(Json.obj("a" -> 1)))
      val result6 = GenericResult(wasSuccessful = false, message = Some("Result #6"), data = Some(Json.obj("b" -> 2)))
      val result7 = GenericResult(wasSuccessful = true, data = Some(Json.obj("c" -> 3)))
      val result8 = GenericResult(wasSuccessful = false, data = Some(Json.obj("d" -> 4)))
      val result9 = GenericResult(wasSuccessful = true, message = Some("Result #9"), data = Some(JsNumber(5)))
      val result10 = GenericResult(wasSuccessful = false, message = Some("Result #10"), data = Some(JsString("6")))

      val combine1with2 = result1.combineWith(result2)
      val combine2with1 = result2.combineWith(result1)
      combine1with2.wasSuccessful should equal(false)
      combine1with2.message should equal(None)
      combine1with2.data should equal(None)
      combine2with1.wasSuccessful should equal(false)
      combine2with1.message should equal(None)
      combine2with1.data should equal(None)

      val combine3with4 = result3.combineWith(result4)
      val combine4with3 = result4.combineWith(result3)
      combine3with4.wasSuccessful should equal(false)
      combine3with4.message should not equal None
      combine3with4.message.get.contains(result3.message.get) should equal(true)
      combine3with4.message.get.contains(result4.message.get) should equal(true)
      combine3with4.data should equal(None)
      combine4with3.wasSuccessful should equal(false)
      combine4with3.message should not equal None
      combine4with3.message.get.contains(result3.message.get) should equal(true)
      combine4with3.message.get.contains(result4.message.get) should equal(true)
      combine4with3.data should equal(None)

      val combine5with6 = result5.combineWith(result6)
      val combine6with5 = result6.combineWith(result5)
      combine5with6.wasSuccessful should equal(false)
      combine5with6.message should not equal None
      combine5with6.message.get.contains(result5.message.get) should equal(true)
      combine5with6.message.get.contains(result6.message.get) should equal(true)
      combine5with6.data should not equal None
      combine5with6.data.get.asInstanceOf[JsObject].value.contains("a") should equal(true)
      combine5with6.data.get.asInstanceOf[JsObject].value.contains("b") should equal(true)
      combine6with5.wasSuccessful should equal(false)
      combine6with5.message should not equal None
      combine6with5.message.get.contains(result5.message.get) should equal(true)
      combine6with5.message.get.contains(result6.message.get) should equal(true)
      combine6with5.data should not equal None
      combine6with5.data.get.asInstanceOf[JsObject].value.contains("a") should equal(true)
      combine6with5.data.get.asInstanceOf[JsObject].value.contains("b") should equal(true)

      val combine7with8 = result7.combineWith(result8)
      val combine8with7 = result8.combineWith(result7)
      combine7with8.wasSuccessful should equal(false)
      combine7with8.message should equal(None)
      combine7with8.data should not equal None
      combine7with8.data.get.asInstanceOf[JsObject].value.contains("c") should equal(true)
      combine7with8.data.get.asInstanceOf[JsObject].value.contains("d") should equal(true)
      combine8with7.wasSuccessful should equal(false)
      combine8with7.message should equal(None)
      combine8with7.data should not equal None
      combine8with7.data.get.asInstanceOf[JsObject].value.contains("c") should equal(true)
      combine8with7.data.get.asInstanceOf[JsObject].value.contains("d") should equal(true)

      val combine9with10 = result9.combineWith(result10)
      val combine10with9 = result10.combineWith(result9)
      combine9with10.wasSuccessful should equal(false)
      combine9with10.message should not equal None
      combine9with10.message.get.contains(result9.message.get) should equal(true)
      combine9with10.message.get.contains(result10.message.get) should equal(true)
      combine9with10.data should not equal None
      combine9with10.data.get.asInstanceOf[JsObject].value.contains("this") should equal(true)
      combine9with10.data.get.asInstanceOf[JsObject].value.contains("other") should equal(true)
      combine10with9.wasSuccessful should equal(false)
      combine10with9.message should not equal None
      combine10with9.message.get.contains(result9.message.get) should equal(true)
      combine10with9.message.get.contains(result10.message.get) should equal(true)
      combine10with9.data should not equal None
      combine10with9.data.get.asInstanceOf[JsObject].value.contains("this") should equal(true)
      combine10with9.data.get.asInstanceOf[JsObject].value.contains("other") should equal(true)

      val combine1with3 = result1.combineWith(result3)
      val combine3with1 = result3.combineWith(result1)
      combine1with3.wasSuccessful should equal(true)
      combine1with3.message should equal(result3.message)
      combine1with3.data should equal(None)
      combine3with1.wasSuccessful should equal(true)
      combine3with1.message should equal(result3.message)
      combine3with1.data should equal(None)

      val combine1with7 = result1.combineWith(result7)
      val combine7with1 = result7.combineWith(result1)
      combine1with7.wasSuccessful should equal(true)
      combine1with7.message should equal(None)
      combine1with7.data should equal(result7.data)
      combine7with1.wasSuccessful should equal(true)
      combine7with1.message should equal(None)
      combine7with1.data should equal(result7.data)

      val combine1with5with9 = result1.combineWith(result5).combineWith(result9)
      val combine9with5with1 = result9.combineWith(result5).combineWith(result1)

      combine1with5with9.wasSuccessful should equal(true)
      combine1with5with9.message should not equal None
      combine1with5with9.message.get.contains(result5.message.get) should equal(true)
      combine1with5with9.message.get.contains(result9.message.get) should equal(true)
      combine1with5with9.data should not equal None
      combine1with5with9.data.get.asInstanceOf[JsObject].value.contains("this") should equal(true)
      combine1with5with9.data.get.asInstanceOf[JsObject].value.contains("other") should equal(true)

      combine9with5with1.wasSuccessful should equal(true)
      combine9with5with1.message should not equal None
      combine9with5with1.message.get.contains(result5.message.get) should equal(true)
      combine9with5with1.message.get.contains(result9.message.get) should equal(true)
      combine9with5with1.data should not equal None
      combine9with5with1.data.get.asInstanceOf[JsObject].value.contains("this") should equal(true)
      combine9with5with1.data.get.asInstanceOf[JsObject].value.contains("other") should equal(true)
  }

  it should "fail if attempting to combine result with itself" in {
    _ =>
      val result = GenericResult(wasSuccessful = true)

      a[IllegalArgumentException] should be thrownBy {
        result.combineWith(result)
      }
  }
}
