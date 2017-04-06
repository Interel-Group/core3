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
package core3.core

import java.util

import core3.core.Component.ActionDescriptor
import org.jline.reader._
import org.jline.utils.{AttributedString, AttributedStyle}

package object cli {

  /**
    * Basic terminal highlighter.
    */
  class BasicHighlighter extends Highlighter {
    override def highlight(reader: LineReader, buffer: String): AttributedString = {
      new AttributedString(buffer, AttributedStyle.DEFAULT.bold())
    }
  }

  /**
    * Basic terminal completer.
    *
    * @param data the component and action data to be used for generating command suggestions
    */
  class BasicCompleter(private val data: Map[String, Seq[ActionDescriptor]]) extends Completer {
    override def complete(reader: LineReader, line: ParsedLine, candidates: util.List[Candidate]): Unit = {
      line.wordIndex() match {
        case 0 => data.keys.foreach(component => candidates.add(new Candidate(component)))

        case 1 =>
          line.words.get(0) match {
            case "help" => data.keys.foreach(component => candidates.add(new Candidate(component)))
            case componentName => data.get(componentName) match {
              case Some(actions) => actions.map(action => candidates.add(new Candidate(action.name)))
              case None => //do nothing
            }
          }

        case 2 =>
          line.words.get(0) match {
            case "help" => data.get(line.words.get(1)) match {
              case Some(actions) => actions.map(action => candidates.add(new Candidate(action.name)))
              case None => //do nothing
            }

            case _ => //do nothing
          }

        case _ => //do nothing
      }
    }
  }

  /**
    * Command configuration container.
    *
    * @param component the component to execute the action/command
    * @param action    the action to be executed
    * @param params    any parameters that may be needed
    */
  case class CommandConfig(
    component: Option[String] = None,
    action: Option[String] = None,
    params: Option[Map[String, Option[String]]] = None
  )

}
