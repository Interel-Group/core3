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
package core3.test.specs.unit.database.containers

import core3.database._
import core3.database.containers._
import play.api.libs.json._

case class LargeContainer(
  int1: Int,
  int2: Int,
  int3: Int,
  int4: Int,
  int5: Int,
  int6: Int,
  int7: Int,
  int8: Int,
  int9: Int,
  int10: Int,
  string1: String,
  string2: String,
  string3: String,
  string4: String,
  string5: String,
  string6: String,
  string7: String,
  string8: String,
  string9: String,
  string10: String,
  decimal1: BigDecimal,
  decimal2: BigDecimal,
  decimal3: BigDecimal,
  decimal4: BigDecimal,
  decimal5: BigDecimal,
  decimal6: BigDecimal,
  decimal7: BigDecimal,
  decimal8: BigDecimal,
  decimal9: BigDecimal,
  decimal10: BigDecimal,
  id: ObjectID
)
  extends ImmutableContainer {
  override val objectType: ContainerType = "LargeContainer"
}

object LargeContainer extends JsonContainerCompanion with SlickContainerCompanion {

  import slick.jdbc.MySQLProfile.api._
  import shapeless._
  import slickless._

  //
  //SlickContainerCompanion Definitions
  //
  private class TableDef(tag: Tag)
    extends Table[LargeContainer](tag, "core_large_container") {
    def int1 = column[Int]("INT_1")
    def int2 = column[Int]("INT_2")
    def int3 = column[Int]("INT_3")
    def int4 = column[Int]("INT_4")
    def int5 = column[Int]("INT_5")
    def int6 = column[Int]("INT_6")
    def int7 = column[Int]("INT_7")
    def int8 = column[Int]("INT_8")
    def int9 = column[Int]("INT_9")
    def int10 = column[Int]("INT_10")
    def string1 = column[String]("STRING_1")
    def string2 = column[String]("STRING_2")
    def string3 = column[String]("STRING_3")
    def string4 = column[String]("STRING_4")
    def string5 = column[String]("STRING_5")
    def string6 = column[String]("STRING_6")
    def string7 = column[String]("STRING_7")
    def string8 = column[String]("STRING_8")
    def string9 = column[String]("STRING_9")
    def string10 = column[String]("STRING_10")
    def decimal1 = column[BigDecimal]("DECIMAL_1")
    def decimal2 = column[BigDecimal]("DECIMAL_2")
    def decimal3 = column[BigDecimal]("DECIMAL_3")
    def decimal4 = column[BigDecimal]("DECIMAL_4")
    def decimal5 = column[BigDecimal]("DECIMAL_5")
    def decimal6 = column[BigDecimal]("DECIMAL_6")
    def decimal7 = column[BigDecimal]("DECIMAL_7")
    def decimal8 = column[BigDecimal]("DECIMAL_8")
    def decimal9 = column[BigDecimal]("DECIMAL_9")
    def decimal10 = column[BigDecimal]("DECIMAL_10")
    def id = column[ObjectID]("ID", O.PrimaryKey)

    def * = (
      int1 :: int2 :: int3 :: int4 :: int5 :: int6 :: int7 :: int8 :: int9 :: int10
        :: string1 :: string2 :: string3 :: string4 :: string5 :: string6 :: string7 :: string8 :: string9 :: string10
        :: decimal1 :: decimal2 :: decimal3 :: decimal4 :: decimal5 :: decimal6 :: decimal7 :: decimal8 :: decimal9 :: decimal10
        :: id
        :: HNil
      ).mappedWith(Generic[LargeContainer])
  }

  private val query = TableQuery[TableDef]
  private val compiledGetByID = Compiled((objectID: Rep[ObjectID]) => query.filter(_.id === objectID))

  override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create

  override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop

  override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result

  override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result

  override def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[LargeContainer]

  override def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write] =
    throw new IllegalStateException("core3.test.specs.unit.database.containers.LargeContainer::runUpdate > Cannot update 'LargeContainer' data.")

  override def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write] =
    throw new IllegalStateException("core3.test.specs.unit.database.containers.LargeContainer::runUpdate > Cannot delete 'LargeContainer' data.")

  override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read] =
    throw new IllegalArgumentException(s"core3.test.specs.unit.database.containers.LargeContainer::runCustomQuery > Query [$queryName] is not supported.")

  //
  //JsonContainerCompanion Definitions
  //
  private val writes = Writes[LargeContainer] {
    obj =>
      Json.obj(
        "int1" -> obj.int1,
        "int2" -> obj.int2,
        "int3" -> obj.int3,
        "int4" -> obj.int4,
        "int5" -> obj.int5,
        "int6" -> obj.int6,
        "int7" -> obj.int7,
        "int8" -> obj.int8,
        "int9" -> obj.int9,
        "int10" -> obj.int10,
        "string1" -> obj.string1,
        "string2" -> obj.string2,
        "string3" -> obj.string3,
        "string4" -> obj.string4,
        "string5" -> obj.string5,
        "string6" -> obj.string6,
        "string7" -> obj.string7,
        "string8" -> obj.string8,
        "string9" -> obj.string9,
        "string10" -> obj.string10,
        "decimal1" -> obj.decimal1,
        "decimal2" -> obj.decimal2,
        "decimal3" -> obj.decimal3,
        "decimal4" -> obj.decimal4,
        "decimal5" -> obj.decimal5,
        "decimal6" -> obj.decimal6,
        "decimal7" -> obj.decimal7,
        "decimal8" -> obj.decimal8,
        "decimal9" -> obj.decimal9,
        "decimal10" -> obj.decimal10,
        "id" -> obj.id
      )
  }

  private val reads = Reads[LargeContainer] {
    json =>
      JsSuccess(
        new LargeContainer(
          (json \ "int1").as[Int],
          (json \ "int2").as[Int],
          (json \ "int3").as[Int],
          (json \ "int4").as[Int],
          (json \ "int5").as[Int],
          (json \ "int6").as[Int],
          (json \ "int7").as[Int],
          (json \ "int8").as[Int],
          (json \ "int9").as[Int],
          (json \ "int10").as[Int],
          (json \ "string1").as[String],
          (json \ "string2").as[String],
          (json \ "string3").as[String],
          (json \ "string4").as[String],
          (json \ "string5").as[String],
          (json \ "string6").as[String],
          (json \ "string7").as[String],
          (json \ "string8").as[String],
          (json \ "string9").as[String],
          (json \ "string10").as[String],
          (json \ "decimal1").as[BigDecimal],
          (json \ "decimal2").as[BigDecimal],
          (json \ "decimal3").as[BigDecimal],
          (json \ "decimal4").as[BigDecimal],
          (json \ "decimal5").as[BigDecimal],
          (json \ "decimal6").as[BigDecimal],
          (json \ "decimal7").as[BigDecimal],
          (json \ "decimal8").as[BigDecimal],
          (json \ "decimal9").as[BigDecimal],
          (json \ "decimal10").as[BigDecimal],
          (json \ "id").as[ObjectID]
        )
      )
  }

  override def toJsonData(container: Container): JsValue = {
    Json.toJson(container.asInstanceOf[LargeContainer])(writes)
  }

  override def fromJsonData(data: JsValue): Container = {
    data.as[LargeContainer](reads)
  }

  //
  //BasicContainerCompanion Definitions
  //
  override def getDatabaseName: String = "core-large-container"

  override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: Container): Boolean =
    throw new IllegalArgumentException(s"core3.test.specs.unit.database.containers.LargeContainer::matchCustomQuery > Query [$queryName] is not supported.")
}
