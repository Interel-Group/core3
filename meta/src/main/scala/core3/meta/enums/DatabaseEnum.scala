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

class DatabaseEnum extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    defn match {
      case Term.Block(Seq(enumTrait @ Defn.Trait(_, enumType, _, _, _), companion: Defn.Object)) =>
        val updatedStats: Seq[Stat] = DatabaseEnum.buildEnumStats(enumType, companion.templ.stats.getOrElse(Seq.empty))
        val updatedCompanion = companion.copy(templ = companion.templ.copy(stats = Some(updatedStats)))
        Term.Block(Seq(enumTrait, updatedCompanion))

      case Term.Block(Seq(enumClass @ Defn.Class(_, enumType, _, _, _), companion: Defn.Object)) =>
        val updatedStats: Seq[Stat] = DatabaseEnum.buildEnumStats(enumType, companion.templ.stats.getOrElse(Seq.empty))
        val updatedCompanion = companion.copy(templ = companion.templ.copy(stats = Some(updatedStats)))
        Term.Block(Seq(enumClass, updatedCompanion))

      case q"..$mods object $enumName { ..$stats }" =>
        val enumType = Type.Name(enumName.syntax)
        val updatedStats = DatabaseEnum.buildEnumStats(enumType, stats)
        q"..$mods object $enumName { ..$updatedStats }"

      case _ =>
        println(defn.structure)
        abort("core3.meta.enums.DatabaseEnum::apply > Must annotate object, trait or class.")
    }
  }
}

object DatabaseEnum {
  def buildEnumStats(enumType: Type.Name, stats: Seq[Stat]): Seq[Stat] = {
    val enumName = Term.Name(enumType.syntax)

    val fromString = CommonDefinitions.fromString(enumName, enumType, stats)

    Seq(fromString) ++: stats
  }
}
