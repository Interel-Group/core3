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
  * Case class annotation for generating [[core3.database.containers.SlickContainerDefinition]] traits in
  * the companion object of the case class.
  * <br>
  * Generates:
  * <ul>
  *   <li>The full definition, when a [[SlickContainerDefinition]] trait is not defined.</li>
  *   <li>The full definition, when a [[SlickContainerDefinition]] trait is defined, but does not implement
  *   the 'matchCustomQuery' method.</li>
  *   <li>The full definition, except the 'customQueryAction' method), when a [[SlickContainerDefinition]]
  *   trait is defined and the 'customQueryAction' method is implemented.</li>
  * </ul>
  */
class WithSlickContainerDefinition extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    def updateCompanion(cls: Defn.Class, companion: Defn.Object): Term.Block = {
      val params = cls.ctor.paramss.flatten

      if (params.nonEmpty) {
        val slickImports = CommonDefinitions.slickImports()
        val slickTable = CommonDefinitions.slickTable(cls.name, params)
        val slickQueries = CommonDefinitions.slickQueries()
        val slickActions = CommonDefinitions.slickActions(cls.name)

        val enumColumnTypes = companion.templ.stats.getOrElse(Seq.empty).flatMap {
          case q"@DatabaseEnum ..$_ trait $enumType {..$_}" =>
            Some(enums.CommonDefinitions.slickColumnType(Term.Name(enumType.syntax), enumType))

          case q"@DatabaseEnum ..$_ object $enumName {..$_}" =>
            Some(enums.CommonDefinitions.slickColumnType(enumName, Type.Name(enumName.syntax)))

          case q"@DatabaseEnum ..$_ class $enumType {..$_}" =>
            Some(enums.CommonDefinitions.slickColumnType(Term.Name(enumType.syntax), enumType))

          case s => None
        }

        val slickStats = slickImports ++ enumColumnTypes ++ Seq(slickTable) ++ slickQueries ++ slickActions

        val existingDefinition = companion.templ.stats.getOrElse(Seq.empty).flatMap {
          case companionTrait: Defn.Trait =>
            if (companionTrait.templ.parents.exists(_.syntax.endsWith("SlickContainerDefinition()"))) {
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
              val hasCustomQueryAction = definitionStats.exists {
                case q"override def customQueryAction(..$_): $_ = $_" => true
                case _ => false
              }

              val updatedDefinition =
                if (hasCustomQueryAction)
                  definition.copy(
                    templ = definition.templ.copy(
                      stats = Some(slickStats.dropRight(1) ++ definitionStats)
                    )
                  )
                else
                  definition.copy(
                    templ = definition.templ.copy(
                      stats = Some(slickStats ++ definitionStats)
                    )
                  )

              Seq(updatedDefinition) ++ companion.templ.stats.get.filter(_ != definition)

            case None =>
              val newDefinition =
                q"""
                    trait SlickDefinition extends core3.database.containers.SlickContainerDefinition {
                      ..$slickStats
                    }
                """

              Seq(newDefinition) ++ companion.templ.stats.getOrElse(Seq.empty)
          }

        val updatedCompanion = companion.copy(templ = companion.templ.copy(stats = Some(updatedStats)))

        Term.Block(Seq(cls, updatedCompanion))
      } else {
        abort(s"core3.meta.containers.WithSlickContainerDefinition::updateCompanion > No fields defined in container [${cls.name}].")
      }
    }

    defn match {
      case Term.Block(Seq(cls: Defn.Class, companion: Defn.Object)) =>
        updateCompanion(cls, companion)

      case cls: Defn.Class =>
        updateCompanion(cls, q"object ${Term.Name(cls.name.syntax)}")

      case _ =>
        println(defn.structure)
        abort("core3.meta.containers.WithSlickContainerDefinition::apply > Must annotate class.")
    }
  }
}
