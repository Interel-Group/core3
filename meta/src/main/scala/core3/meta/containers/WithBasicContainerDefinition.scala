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

/**
  * Case class annotation for generating [[core3.database.containers.BasicContainerDefinition]] traits in
  * the companion object of the case class.
  * <br>
  * Generates:
  * <ul>
  *   <li>The full definition, when a [[BasicContainerDefinition]] trait is not defined.</li>
  *   <li>The full definition, when a [[BasicContainerDefinition]] trait is defined, but does not implement
  *   the 'matchCustomQuery' method.</li>
  *   <li>The full definition, except the 'matchCustomQuery' method), when a [[BasicContainerDefinition]]
  *   trait is defined and the 'matchCustomQuery' method is implemented.</li>
  * </ul>
  */
class WithBasicContainerDefinition extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    def updateCompanion(cls: Defn.Class, companion: Defn.Object): Term.Block = {
      val params = cls.ctor.paramss.flatten

      if (params.nonEmpty) {
        val getDatabaseName = CommonDefinitions.getDatabaseName(cls.name)
        val matchCustomQuery = CommonDefinitions.matchCustomQuery()

        val existingDefinition = companion.templ.stats.getOrElse(Seq.empty).flatMap {
          case companionTrait: Defn.Trait =>
            if (companionTrait.templ.parents.exists(_.syntax.endsWith("BasicContainerDefinition()"))) {
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
              val hasMatchCustomQuery = definitionStats.exists {
                case q"override def matchCustomQuery(..$_): $_ = $_" => true
                case _ => false
              }

              val updatedDefinition =
                if (hasMatchCustomQuery)
                  definition.copy(
                    templ = definition.templ.copy(
                      stats = Some(Seq(getDatabaseName) ++ definitionStats)
                    )
                  )
                else
                  definition.copy(
                    templ = definition.templ.copy(
                      stats = Some(Seq(getDatabaseName, matchCustomQuery) ++ definitionStats)
                    )
                  )

              Seq(updatedDefinition) ++ companion.templ.stats.get.filter(_ != definition)

            case None =>
              val newDefinition =
                q"""
                    trait BasicDefinition extends core3.database.containers.BasicContainerDefinition {
                      $getDatabaseName
                      $matchCustomQuery
                    }
                """

              Seq(newDefinition) ++ companion.templ.stats.getOrElse(Seq.empty)
          }

        val updatedCompanion = companion.copy(templ = companion.templ.copy(stats = Some(updatedStats)))

        Term.Block(Seq(cls, updatedCompanion))
      } else {
        abort(s"core3.meta.containers.WithBasicContainerDefinition::updateCompanion > No fields defined in container [${cls.name}].")
      }
    }

    defn match {
      case Term.Block(Seq(cls: Defn.Class, companion: Defn.Object)) =>
        updateCompanion(cls, companion)

      case cls: Defn.Class =>
        updateCompanion(cls, q"object ${Term.Name(cls.name.syntax)}")

      case _ =>
        println(defn.structure)
        abort("core3.meta.containers.WithBasicContainerDefinition::apply > Must annotate class.")
    }
  }
}
