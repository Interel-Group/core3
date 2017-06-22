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
import core3.meta.enums

/**
  * Case class annotation for generating [[core3.database.containers.JsonContainerDefinition]] traits in
  * the companion object of the case class.
  * <br>
  * Generates:
  * <ul>
  *   <li>The full definition, when a [[JsonContainerDefinition]] trait is not defined.</li>
  *   <li>The full definition, when a [[JsonContainerDefinition]] trait is defined.</li>
  * </ul>
  */
class WithJsonContainerDefinition extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    def updateCompanion(cls: Defn.Class, companion: Defn.Object): Term.Block = {
      val params = cls.ctor.paramss.flatten

      if (params.nonEmpty) {
        def generateEnumImplicits(enumName: Term.Name, enumType: Type.Name) =
          Some(
            enums.CommonDefinitions.jsonWrites(enumName, enumType),
            enums.CommonDefinitions.jsonReads(enumName, enumType)
          )

        val (enumWrites, enumReads) = companion.templ.stats.getOrElse(Seq.empty).flatMap {
          case q"@DatabaseEnum ..$_ trait $enumType {..$_}" =>
            generateEnumImplicits(Term.Name(enumType.syntax), enumType)

          case q"@DatabaseEnum ..$_ object $enumName {..$_}" =>
            generateEnumImplicits(enumName, Type.Name(enumName.syntax))

          case q"@DatabaseEnum ..$_ class $enumType {..$_}" =>
            generateEnumImplicits(Term.Name(enumType.syntax), enumType)

          case s => None
        }.unzip

        val jsonWrites = CommonDefinitions.jsonWrites(cls.name, params)
        val jsonReads = CommonDefinitions.jsonReads(cls.name, params)

        val toJsonData = CommonDefinitions.toJsonData(cls.name)
        val fromJsonData = CommonDefinitions.fromJsonData(cls.name)

        val implicitStats = enumWrites ++ enumReads ++ Seq(jsonWrites, jsonReads)

        val existingDefinition = companion.templ.stats.getOrElse(Seq.empty).flatMap {
          case companionTrait: Defn.Trait =>
            if (companionTrait.templ.parents.exists(_.syntax.endsWith("JsonContainerDefinition()"))) {
              Some(companionTrait)
            } else {
              None
            }

          case _ => None
        }.headOption

        val updatedStats: Seq[Stat] =
          existingDefinition match {
            case Some(definition) =>
              val definitionStats = definition.templ.stats.getOrElse(Seq.empty)

              val updatedDefinition =
                definition.copy(
                  templ = definition.templ.copy(
                    stats = Some(Seq(toJsonData, fromJsonData) ++ definitionStats)
                  )
                )

              implicitStats ++ Seq(updatedDefinition) ++ companion.templ.stats.get.filter(_ != definition)

            case None =>
              val newDefinition =
                q"""
                    trait JsonDefinition extends core3.database.containers.JsonContainerDefinition {
                      $toJsonData
                      $fromJsonData
                    }
                """

              implicitStats ++ Seq(newDefinition) ++ companion.templ.stats.getOrElse(Seq.empty)
          }

        val updatedCompanion = companion.copy(templ = companion.templ.copy(stats = Some(updatedStats)))

        Term.Block(Seq(cls, updatedCompanion))
      } else {
        abort(s"core3.meta.containers.WithJsonContainerDefinition::apply > No fields defined in container [${cls.name}].")
      }
    }

    defn match {
      case Term.Block(Seq(cls: Defn.Class, companion: Defn.Object)) =>
        updateCompanion(cls, companion)

      case cls: Defn.Class =>
        updateCompanion(cls, q"object ${Term.Name(cls.name.syntax)}")

      case _ =>
        println(defn.structure)
        abort("core3.meta.containers.WithJsonContainerDefinition::apply > Must annotate class.")
    }
  }
}
