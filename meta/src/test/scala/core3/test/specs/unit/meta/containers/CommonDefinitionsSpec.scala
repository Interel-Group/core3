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
package core3.test.specs.unit.meta.containers

import core3.meta.containers._
import core3.test.specs.unit.meta.structurallyEqual
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Seq
import scala.meta._

class CommonDefinitionsSpec extends FlatSpec with Matchers {
  "A set of common enum definitions" should "successfully generate a 'getDatabaseName' method" in {
    structurallyEqual(
      generated = CommonDefinitions.getDatabaseName(Type.Name("TestContainer")),
      expected = q"""override def getDatabaseName: String = "test_container""""
    )

    structurallyEqual(
      generated = CommonDefinitions.getDatabaseName(Type.Name("TestContainer"), Some("custom_database_name")),
      expected = q"""override def getDatabaseName: String = "custom_database_name""""
    )
  }

  it should "successfully generate a 'matchCustomQuery' method" in {
    structurallyEqual(
      generated = CommonDefinitions.matchCustomQuery(),
      expected = q"""override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: core3.database.containers.Container): Boolean = ???"""
    )
  }

  it should "successfully generate 'jsonReads' and 'jsonWrites' implicit vals" in {
    structurallyEqual(
      generated = CommonDefinitions.jsonReads(Type.Name("TestContainer"), Seq(param"val1: String", param"val2: Timestamp", param"val3: TestEnum", param"id: ObjectID")),
      expected =
        q"""
            implicit val TestContainerReads: play.api.libs.json.Reads[TestContainer] = play.api.libs.json.Reads[TestContainer] {
              json => play.api.libs.json.JsSuccess(
                new TestContainer(
                  (json \ "val1").as[String],
                  (json \ "val2").as[Timestamp],
                  (json \ "val3").as[TestEnum],
                  (json \ "id").as[ObjectID]
                )
              )
            }
        """
    )

    structurallyEqual(
      generated = CommonDefinitions.jsonWrites(Type.Name("TestContainer"), Seq(param"val1: String", param"val2: Timestamp", param"val3: TestEnum", param"id: ObjectID")),
      expected =
        q"""
            implicit val TestContainerWrites: play.api.libs.json.Writes[TestContainer] = play.api.libs.json.Writes[TestContainer] {
              obj =>
                play.api.libs.json.Json.obj(
                  "val1" -> obj.val1,
                  "val2" -> obj.val2,
                  "val3" -> obj.val3,
                  "id" -> obj.id
                )
            }
        """
    )
  }

  it should "successfully generate 'fromJsonData' and 'toJsonData' methods" in {
    structurallyEqual(
      generated = CommonDefinitions.fromJsonData(Type.Name("TestContainer")),
      expected =
        q"""override def fromJsonData(data: play.api.libs.json.JsValue): core3.database.containers.Container = { data.as[TestContainer] }"""
    )

    structurallyEqual(
      generated = CommonDefinitions.toJsonData(Type.Name("TestContainer")),
      expected =
        q"""
            override def toJsonData(container: core3.database.containers.Container): play.api.libs.json.JsValue = {
              play.api.libs.json.Json.toJson(container.asInstanceOf[TestContainer])
            }
        """
    )
  }

  it should "successfully generate imports for a SlickContainerDefinition" in {
    CommonDefinitions.slickImports()
      .zip(
        Seq(
          q"import profile.api._",
          q"import shapeless._",
          q"import slickless._"
        )
      )
      .foreach {
        case (generated, expected) => structurallyEqual(generated, expected)
      }
  }

  it should "successfully generate a SlickContainerDefinition table" in {
    structurallyEqual(
      generated = CommonDefinitions.slickTable(
        Type.Name("TestContainer"),
        Seq(param"val1: String", param"val2: Timestamp", param"val3: TestEnum", param"val4: Time", param"id: ObjectID")
      ),
      expected =
        q"""
          private class TableDef(tag: Tag) extends Table[TestContainer](tag, "test_container") {
            def val1 = column[String]("VAL1")
            def val2 = column[Timestamp]("VAL2", O.SqlType("DATETIME(3)"))
            def val3 = column[TestEnum]("VAL3")
            def val4 = column[Time]("VAL4", O.SqlType("TIME(3)"))
            def id = column[ObjectID]("ID", O.PrimaryKey)
            def * = (val1 :: val2 :: val3 :: val4 :: id :: HNil).mappedWith(Generic[TestContainer])
          }
        """
    )

    structurallyEqual(
      generated = CommonDefinitions.slickTable(
        Type.Name("TestContainer"),
        Seq(param"val1: String", param"val2: Timestamp", param"val3: TestEnum", param"id: ObjectID"),
        Some("custom_table_name")
      ),
      expected =
        q"""
          private class TableDef(tag: Tag) extends Table[TestContainer](tag, "custom_table_name") {
            def val1 = column[String]("VAL1")
            def val2 = column[Timestamp]("VAL2", O.SqlType("DATETIME(3)"))
            def val3 = column[TestEnum]("VAL3")
            def id = column[ObjectID]("ID", O.PrimaryKey)
            def * = (val1 :: val2 :: val3 :: id :: HNil).mappedWith(Generic[TestContainer])
          }
        """
    )
  }

  it should "successfully generate SlickContainerDefinition queries" in {
    CommonDefinitions.slickQueries()
      .zip(
        Seq(
          q"""private val query = TableQuery[TableDef]""",
          q"""private val compiledGetByID = Compiled((objectID: Rep[core3.database.ObjectID]) => query.filter(_.id === objectID))"""
        )
      )
      .foreach {
        case (generated, expected) => structurallyEqual(generated, expected)
      }
  }

  it should "successfully generate SlickContainerDefinition actions" in {
    CommonDefinitions.slickActions(Type.Name("TestContainer"))
      .zip(
        Seq(
          q"""override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create""",
          q"""override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop""",
          q"""override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result""",
          q"""override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result""",
          q"""override def createAction(container: core3.database.containers.Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[TestContainer]""",
          q"""override def updateAction(container: core3.database.containers.MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[TestContainer])""",
          q"""override def deleteAction(objectID: core3.database.ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete""",
          q"""override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[core3.database.containers.Container], NoStream, Effect.Read] = ???"""
        )
      )
      .foreach {
        case (generated, expected) => structurallyEqual(generated, expected)
      }
  }
}
