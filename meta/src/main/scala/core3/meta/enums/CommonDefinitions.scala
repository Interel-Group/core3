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
package core3.meta.enums

import scala.collection.immutable.Seq
import scala.meta._

object CommonDefinitions {
  /**
    * Generates a 'fromString' method for an enum that attempts to convert a string to that enum.
    * <br>
    * For example:
    * <pre>
    *   sealed trait UserType
    *   object UserType {
    *     case object Client extends UserType
    *     case object Service extends UserType
    *   }
    * </pre>
    * will have the following method generated:
    * <pre>
    *   def fromString(value: String): UserType = {
    *     value match {
    *       case "Client" => UserType.Client
    *       case "Service" => UserType.Service
    *     }
    *   }
    * </pre>
    *
    * @param enumName enumeration Term
    * @param enumType enumeration Type
    * @param stats existing companion object statements
    * @return the method definition
    */
  def fromString(enumName: Term.Name, enumType: Type.Name, stats: Seq[Stat]): Defn.Def = {

    val constants = stats.flatMap {
      case q"case object $constName extends $traitName" =>
        if (s"$enumType()" == traitName.syntax) Some(constName)
        else None
      case _ => None
    }

    if (constants.isEmpty)
      abort("core3.meta.enums.CommonDefinitions::fromString > No enumeration constants found.")

    val matches = constants.map {
      constant =>
        p"""case ${constant.value} => $enumName.$constant"""
    }

    q"""
        def fromString(string: String): $enumType = {
          string match {
            .. case $matches
          }
        }
    """
  }

  /**
    * Generates an implicit val for converting the enum to/from Slick columns.
    * <br>
    * For example:
    * <pre>
    *   sealed trait UserType
    *   object UserType {
    *     case object Client extends UserType
    *     case object Service extends UserType
    *   }
    * </pre>
    * will have the following val generated:
    * <pre>
    *   implicit val UserTypeColumnType = MappedColumnType.base[UserType, String](
    *     { enum => enum.toString },
    *     { string => UserType.fromString(string) }
    *   )
    * </pre>
    *
    * @param enumName enumeration Term
    * @param enumType enumeration Type
    * @return the implicit val definition
    */
  def slickColumnType(enumName: Term.Name, enumType: Type.Name): Defn.Val = {
    val valName = Pat.Var.Term(Term.Name(s"${enumName.syntax}ColumnType"))

    q"""
        implicit val $valName = MappedColumnType.base[$enumType, String](
          { enum => enum.toString },
          { string => $enumName.fromString(string) }
        )
    """
  }

  /**
    * Generates an implicit val for converting a JsValue/JsString to the enum.
    * <br>
    * For example:
    * <pre>
    *   sealed trait UserType
    *   object UserType {
    *     case object Client extends UserType
    *     case object Service extends UserType
    *   }
    * </pre>
    * will have the following val generated:
    * <pre>
    *   implicit val UserTypeReads: Reads[UserType] = Reads {
    *     json => json.validate[String].map(UserType.fromString)
    *   }
    * </pre>
    *
    * @param enumName enumeration Term
    * @param enumType enumeration Type
    * @return the implicit val definition
    */
  def jsonReads(enumName: Term.Name, enumType: Type.Name): Defn.Val = {
    val valName = Pat.Var.Term(Term.Name(s"${enumName.syntax}Reads"))

    q"""
        implicit val $valName: play.api.libs.json.Reads[$enumType] = play.api.libs.json.Reads {
          json => json.validate[String].map($enumName.fromString)
        }
    """
  }


  /**
    * Generates an implicit val for converting the enum to a JsString.
    * <br>
    * For example:
    * <pre>
    *   sealed trait UserType
    *   object UserType {
    *     case object Client extends UserType
    *     case object Service extends UserType
    *   }
    * </pre>
    * will have the following val generated:
    * <pre>
    *   implicit val UserTypeWrites: Writes[UserType] = Writes {
    *     enum => JsString(enum.toString)
    *   }
    * </pre>
    *
    * @param enumName enumeration Term
    * @param enumType enumeration Type
    * @return the implicit val definition
    */
  def jsonWrites(enumName: Term.Name, enumType: Type.Name): Defn.Val = {
    val valName = Pat.Var.Term(Term.Name(s"${enumName.syntax}Writes"))

    q"""
        implicit val $valName: play.api.libs.json.Writes[$enumType] = play.api.libs.json.Writes {
          enum => play.api.libs.json.JsString(enum.toString)
        }
    """
  }
}
