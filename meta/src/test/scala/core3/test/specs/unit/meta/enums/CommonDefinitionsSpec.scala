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
package core3.test.specs.unit.meta.enums

import org.scalatest.{Matchers, FlatSpec}
import scala.collection.immutable.Seq
import scala.meta._
import core3.test.specs.unit.meta.structurallyEqual
import core3.meta.enums._

class CommonDefinitionsSpec extends FlatSpec with Matchers {
  "A set of common enum definitions" should "successfully generate a 'fromString' method" in {
    structurallyEqual(
      generated = CommonDefinitions.fromString(
        Term.Name("TestEnum"),
        Type.Name("TestEnum"),
        Seq(
          q"case object A extends TestEnum()",
          q"case object B extends TestEnum()"
        )
      ),
      expected =
        q"""
          def fromString(string: String): TestEnum = {
            string match {
              case "A" => TestEnum.A
              case "B" => TestEnum.B
            }
          }
      """
    )
  }

  it should "successfully generate a 'slickColumnType' implicit val" in {
    structurallyEqual(
      generated =
        CommonDefinitions.slickColumnType(Term.Name("TestEnum"), Type.Name("TestEnum")),
      expected =
        q"""
          implicit val TestEnumColumnType = MappedColumnType.base[TestEnum, String](
            { enum => enum.toString },
            { string => TestEnum.fromString(string) }
          )
      """
    )
  }

  it should "successfully generate 'jsonReads' and 'jsonWrites' implicit vals" in {
    structurallyEqual(
      generated = CommonDefinitions.jsonReads(Term.Name("TestEnum"), Type.Name("TestEnum")),
      expected =
        q"""
            implicit val TestEnumReads: play.api.libs.json.Reads[TestEnum] = play.api.libs.json.Reads {
              json => json.validate[String].map(TestEnum.fromString)
            }
        """
    )

    structurallyEqual(
      generated = CommonDefinitions.jsonWrites(Term.Name("TestEnum"), Type.Name("TestEnum")),
      expected =
        q"""
            implicit val TestEnumWrites: play.api.libs.json.Writes[TestEnum] = play.api.libs.json.Writes {
              enum => play.api.libs.json.JsString(enum.toString)
            }
        """
    )
  }
}
