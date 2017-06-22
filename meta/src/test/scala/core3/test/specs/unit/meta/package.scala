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
package core3.test.specs.unit

import scala.meta.Tree
import scala.meta.testkit.{AnyDiff, StructurallyEqual}

package object meta {
  def structurallyEqual(generated: Tree, expected: Tree): Unit = StructurallyEqual(generated, expected) match {
    case Left(AnyDiff(gen, exp)) => throw new RuntimeException(s"Structure not equal:\n- Generated: $gen\n- Expected: $exp")
    case _ =>
  }
}
