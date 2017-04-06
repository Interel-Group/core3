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
package core3.test.specs.prop

import java.util.Currency

import core3.database.ObjectID
import core3.database.containers._
import core3.database.containers.core._
import core3.utils.{Time, Timestamp}
import org.scalacheck.Arbitrary._
import org.scalacheck._
import play.api.libs.json.{JsString, JsValue, Json}

object Generators {
  private val maxJsonObjectSize = 20
  private val maxItemsListSize = 20

  /**
    * Generator for lists of [[core3.database.ObjectID]]
    */
  val generateObjectIDsList: Gen[Vector[ObjectID]] = Gen.sized {
    size =>
      Gen.listOfN(if (size > maxItemsListSize) maxItemsListSize else size, Gen.uuid).flatMap(_.to[Vector])
  }

  /**
    * Generator for lists of Strings
    */
  val generateStringsList: Gen[Vector[String]] = Gen.sized {
    size =>
      Gen.listOfN(
        if (size > maxItemsListSize) maxItemsListSize else size,
        arbitrary[String] suchThat (c => c.length > 0 && !c.contains(","))).flatMap(_.to[Vector])
  }

  /**
    * Generator for [[java.util.Currency]]
    */
  val generateCurrency: Gen[Currency] = {
    Gen.oneOf(Set(Currency.getAvailableCurrencies.toArray: _*).toSeq.map(_.asInstanceOf[Currency]))
  }

  /**
    * Generator for [[core3.utils.Timestamp]]
    */
  val generateTimestamp: Gen[Timestamp] = {
    Gen.resultOf[Int, Timestamp](ms => Time.getCurrentTimestamp.plusMillis(ms))
  }

  /**
    * Generator for param tuples (String, JsValue)
    */
  val generateJsonTuple: Gen[(String, JsValue)] = for {
    k <- Gen.chooseNum(0, 100) map (_.toString)
    v <- arbitrary[String] suchThat (c => c.length > 0)
  } yield (k, JsString(v))

  /**
    * Generator for JsValue objects.
    */
  val generateJsonObject: Gen[JsValue] = Gen.sized {
    size =>
      Gen.mapOfN[String, JsValue](if (size > maxJsonObjectSize) maxJsonObjectSize else size, generateJsonTuple)
        .flatMap {
          jsonMap =>
            val fields = jsonMap
              .map { case (k, v) => k -> Json.toJsFieldJsValueWrapper(v) }
              .toSeq
            Json.obj(fields: _*)
        }
  }

  /**
    * Generator for [[core3.database.containers.core.TransactionLog]] containers.
    */
  val generateTransactionLog: Gen[TransactionLog] = for {
    workflowName <- arbitrary[String] suchThat (c => c.length > 0)
    requestID <- Gen.uuid
    readOnlyWorkflow <- arbitrary[Boolean]
    parameters <- generateJsonObject
    data <- generateJsonObject
    initiatingUser <- arbitrary[String] suchThat (c => c.length > 0)
    workflowResult <- arbitrary[Boolean]
    workflowState <- arbitrary[String] suchThat (c => c.length > 0)
  } yield new core.TransactionLog(workflowName, requestID, readOnlyWorkflow, parameters, data, initiatingUser, workflowResult, workflowState)

  /**
    * Generator for [[core3.database.containers.core.Group]] containers.
    */
  val generateGroup: Gen[Group] = for {
    shortName <- arbitrary[String] suchThat (c => c.length > 0) map (c => if (c.length < 32) c else c.substring(0, 32))
    name <- arbitrary[String]
    items <- generateObjectIDsList
    itemsType <- Gen.oneOf("TransactionLog", "Group")
    createdBy <- Gen.uuid
  } yield new core.Group(shortName, name, items, itemsType, createdBy.toString)

  /**
    * Generator for [[core3.database.containers.core.LocalUser]] containers.
    */
  val generateLocalUser: Gen[LocalUser] = for {
    userID <- arbitrary[String] suchThat (c => c.length > 0) map (c => if (c.length < 128) c else c.substring(0, 128))
    hashedPassword <- arbitrary[String]
    passwordSalt <- arbitrary[String]
    permissions <- generateStringsList
    userType <- Gen.oneOf(UserType.Service, UserType.Client)
    metadata <- generateJsonObject
    createdBy <- Gen.uuid
  } yield new core.LocalUser(userID, hashedPassword, passwordSalt, permissions, userType, metadata, createdBy.toString)
}
