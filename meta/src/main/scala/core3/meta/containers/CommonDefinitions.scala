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
package core3.meta.containers

import scala.collection.immutable.Seq
import scala.meta._

object CommonDefinitions {
  private val camelCaseSplitRegex = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"

  /***
    * Generates a 'getDatabaseName' method that overrides the method defined in 'BasicContainerDefinition'.
    * <br>
    * For example:
    * <pre>
    *   case class SomeClass(fieldOne: String, fieldTwo: Int)
    * </pre>
    * will have the following method generated:
    * <pre>
    *   override def getDatabaseName: String = "some_class"
    * </pre>
    *
    * @param containerType container Type
    * @param databaseName (optional) database name to be used; if not specified, the name will based on the container type
    * @return the method definition
    */
  def getDatabaseName(containerType: Type.Name, databaseName: Option[String] = None): Defn.Def = {
    val actualDatabaseName = databaseName.getOrElse(containerType.syntax.split(camelCaseSplitRegex).map(_.toLowerCase).mkString("_"))

    q"""override def getDatabaseName: String = $actualDatabaseName"""
  }

  /***
    * Generates an unimplemented 'matchCustomQuery' that overrides the method defined in 'BasicContainerDefinition'.
    *
    * @return the method definition
    */
  def matchCustomQuery(): Defn.Def = {
    q"""override def matchCustomQuery(queryName: String, queryParams: Map[String, String], container: core3.database.containers.Container): Boolean = ???"""
  }

  /**
    * Generates an implicit val for converting an instance of the annotated container to a JsValue.
    * <br>
    * For example:
    * <pre>
    *   case class SomeClass(fieldOne: String, fieldTwo: Int)
    * </pre>
    * will have the following val generated:
    * <pre>
    *   implicit val SomeClassWrites: Writes[SomeClass] = Writes[SomeClass] {
    *     obj =>
    *       Json.obj(
    *         "fieldOne" -> obj.fieldOne,
    *         "fieldTwo" -> obj.fieldTwo
    *       )
    *   }
    * </pre>
    *
    * @param containerType container Type
    * @param params the parameters defined for the container
    * @return the implicit val definitions
    */
  def jsonWrites(containerType: Type.Name, params: Seq[Term.Param]): Defn.Val = {
    val fields = params.map {
      field =>
        q"""${field.name.value} -> obj.${Term.Name(field.name.value)}"""
    }

    val valName = Pat.Var.Term(Term.Name(s"${containerType.syntax}Writes"))

    q"""
        implicit val $valName: play.api.libs.json.Writes[$containerType] = play.api.libs.json.Writes[$containerType] {
          obj => play.api.libs.json.Json.obj(..$fields)
        }
    """
  }

  /**
    * Generates an implicit val for converting a JsValue to an instance of the annotated container.
    * <br>
    * For example:
    * <pre>
    *   case class SomeClass(fieldOne: String, fieldTwo: Int)
    * </pre>
    * will have the following val generated:
    * <pre>
    *   implicit val SomeClassReads: Reads[SomeClass] = Reads[SomeClass] {
    *     json =>
    *       JsSuccess(
    *         new SomeClass(
    *           (json \ "fieldOne").as[String],
    *           (json \ "fieldTwo").as[Int]
    *         )
    *       )
    *   }
    * </pre>
    *
    * @param containerType container Type
    * @param params the parameters defined for the container
    * @return the implicit val definitions
    */
  def jsonReads(containerType: Type.Name, params: Seq[Term.Param]): Defn.Val = {
    val fields = params.map {
      field =>
        field.decltpe match {
          case Some(targ"Option[$fieldType]") => q"""(json \ ${field.name.value}).asOpt[${Type.Name(fieldType.syntax)}]"""
          case Some(fieldType) => q"""(json \ ${field.name.value}).as[${Type.Name(fieldType.syntax)}]"""
          case None => abort(s"core3.meta.containers.CommonDefinitions::jsonReads > Type for field [${field.name}] is missing from container [$containerType].")
        }
    }

    val valName = Pat.Var.Term(Term.Name(s"${containerType.syntax}Reads"))

    q"""
        implicit val $valName: play.api.libs.json.Reads[$containerType] = play.api.libs.json.Reads[$containerType] {
          json => play.api.libs.json.JsSuccess(new ${Ctor.Ref.Name(containerType.value)}(..$fields))
        }
    """
  }

  /**
    * Generates a 'toJsonData' method for converting an instance of the annotated container to a JsValue via the implicit
    * val generated with [[core3.meta.containers.CommonDefinitions.jsonWrites]].
    *
    * @param containerType container type
    * @return the method definition
    */
  def toJsonData(containerType: Type.Name): Defn.Def = {
    q"""
        override def toJsonData(container: core3.database.containers.Container): play.api.libs.json.JsValue = {
          play.api.libs.json.Json.toJson(container.asInstanceOf[$containerType])
        }
     """
  }

  /**
    * Generates a 'fromJsonData' method for converting a JsValue to an instance of the annotated container via the implicit
    * val generated with [[core3.meta.containers.CommonDefinitions.jsonReads]].
    *
    * @param containerType container type
    * @return the method definition
    */
  def fromJsonData(containerType: Type.Name): Defn.Def = {
    q"""override def fromJsonData(data: play.api.libs.json.JsValue): core3.database.containers.Container = { data.as[$containerType] }"""
  }

  /**
    * Generates the default Slick imports required for the 'SlickContainerDefinition' trait.
    *
    * @return the default imports
    */
  def slickImports(): Seq[Import] = {
    Seq(
      q"import profile.api._",
      q"import shapeless._",
      q"import slickless._"
    )
  }

  /**
    * Generates a Slick table definition for the annotated container.
    * <br>
    * For example:
    * <pre>
    *   case class SomeClass(fieldOne: String, fieldTwo: Int)
    * </pre>
    * will have the following val generated:
    * <pre>
    *   private class TableDef(tag: Tag) extends Table[SomeClass](tag, "some_class") {
    *     def fieldOne = column[String]("FIELD_ONE")
    *     def fieldTwo = column[Int]("FIELD_TWO")
    *     def * = (fieldOne :: fieldTwo :: HNil).mappedWith(Generic[SomeClass])
    *   }
    * </pre>
    *
    * @param containerType container Type
    * @param params the parameters defined for the container
    * @param tableName (optional) table name to be used; if not specified, the name will based on the container type
    * @return the Slick table definition
    */
  def slickTable(containerType: Type.Name, params: Seq[Term.Param], tableName: Option[String] = None): Defn.Class = {
    val actualTableName = tableName.getOrElse(containerType.syntax.split(camelCaseSplitRegex).map(_.toLowerCase).mkString("_"))

    val columnDefs = params.map {
      field =>
        field.decltpe match {
          case Some(fieldType) =>
            val columnName = field.name.value.split(camelCaseSplitRegex).map(_.toUpperCase).mkString("_")
            val columnOptions =
              if(field.name.value == "id") Some(q"""O.PrimaryKey""")
              else if(fieldType.syntax.matches("""(?:Option\[)?.*Timestamp\]?$""")) Some(q"""O.SqlType("DATETIME(3)")""")
              else if(fieldType.syntax.matches("""(?:Option\[)?.*Time\]?$""")) Some(q"""O.SqlType("TIME(3)")""")
              else None

            columnOptions match {
              case Some(options) => q""" def ${Term.Name(field.name.value)} = column[${Type.Name(fieldType.syntax)}]($columnName, $options) """
              case None => q""" def ${Term.Name(field.name.value)} = column[${Type.Name(fieldType.syntax)}]($columnName) """
            }

          case None => abort(s"core3.meta.containers.CommonDefinitions::slickTable > Type for field [${field.name}] is missing from container [$containerType].")
        }
    }

    val columnHlist = params.foldRight[Term](q"""HNil""") {
      (param, folded) => q"""${Term.Name(param.name.value)} :: $folded"""
    }

    val defaultProjectionDef = q"""def * = ($columnHlist).mappedWith(Generic[$containerType])"""

    q"""
      private class TableDef(tag: Tag) extends Table[$containerType](tag, $actualTableName) {
        ..${columnDefs ++ Seq(defaultProjectionDef)}
      }
    """
  }

  /**
    * Generates the default Slick table queries for the annotated container.
    *
    * @return the default queries
    */
  def slickQueries(): Seq[Defn.Val] = {
    Seq(
      q"""private val query = TableQuery[TableDef]""",
      q"""private val compiledGetByID = Compiled((objectID: Rep[core3.database.ObjectID]) => query.filter(_.id === objectID))"""
    )
  }

  /**
    * Generates the default Slick actions for the annotated container (create & drop schema, query, insert, update, delete).
    *
    * @param containerType container Type
    * @return the default actions
    */
  def slickActions(containerType: Type.Name): Seq[Defn.Def] = {
    Seq(
      q"""override def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create""",
      q"""override def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.drop""",
      q"""override def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read] = query.result""",
      q"""override def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read] = compiledGetByID(objectID).result""",
      q"""override def createAction(container: core3.database.containers.Container): DBIOAction[Int, NoStream, Effect.Write] = query += container.asInstanceOf[$containerType]""",
      q"""override def updateAction(container: core3.database.containers.MutableContainer): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(container.id).update(container.asInstanceOf[$containerType])""",
      q"""override def deleteAction(objectID: core3.database.ObjectID): DBIOAction[Int, NoStream, Effect.Write] = compiledGetByID(objectID).delete""",
      q"""override def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[core3.database.containers.Container], NoStream, Effect.Read] = ???"""
    )
  }
}
