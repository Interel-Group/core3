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
package core3.core.cli

import akka.actor.ActorRef
import akka.util.Timeout
import core3.core.Component.ComponentDescriptor
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import play.api.libs.json.Json

import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

/**
  * Class for processing local console commands.
  *
  * @param appVendor        the name of the app vendor
  * @param appName          the name of the app
  * @param appVersion       the current app version
  * @param manager          the system manager
  * @param components       descriptors for the components in use by the system
  * @param enableStackTrace set to true, to enable stack traces on command exceptions;
  *                         set to false to show only exception message
  */
class LocalConsole(
  appVendor: String,
  appName: String,
  appVersion: String,
  manager: ActorRef,
  components: Vector[ComponentDescriptor],
  private var enableStackTrace: Boolean
)(implicit ec: ExecutionContext, timeout: Timeout) {
  private val parser = CommandParser(appVendor, appName, appVersion, components)

  private def printToConsole(message: String): Unit = {
    Console.out.println(s"|  ${message.replace("\n", "\n|  ")}")
  }

  private val term = TerminalBuilder.terminal()

  private val reader = LineReaderBuilder.builder()
    .terminal(term)
    .appName(appName)
    .completer(new BasicCompleter(parser.getComponentAutoCompleteData))
    .highlighter(new BasicHighlighter)
    .build()

  private val prompt = ">: "

  /**
    * Starts the local console and blocks until the user quits.
    */
  def start(): Unit = {
    while (true) {
      try {
        reader.readLine(prompt).trim match {
          case "" => //do nothing
          case "show-trace" => enableStackTrace = true
          case "hide-trace" => enableStackTrace = false
          case line =>
            parser.parse(line.split(" ").toVector, CommandConfig()) match {
              case Some(config) => config.component.get match {
                case "help" => printToConsole(parser.usageMessage(config.action))
                case "version" => //do nothing
                case "exit" => System.exit(0)

                case _ =>
                  val result = CommandRouter.route(manager, config).map {
                    result =>
                      if (result.wasSuccessful) {
                        result.data.map(Json.prettyPrint).orElse(result.message).getOrElse("OK")
                      } else {
                        s"Operation failed with message: [${result.message.getOrElse("No error message available")}]"
                      }
                  }.map(printToConsole).recover {
                    case NonFatal(e) =>
                      if (enableStackTrace) e.printStackTrace()
                      else printToConsole(e.getMessage)
                  }

                  Await.result(result, atMost = timeout.duration)
              }

              case None => //do nothing
            }
        }
      } catch {
        case NonFatal(e) => Option(e.getMessage) match {
          case Some(message) => printToConsole(s"Exception encountered: [$message]")
          case None => //do nothing
        }
      }
    }
  }
}

object LocalConsole {
  def apply(
    appVendor: String,
    appName: String,
    appVersion: String,
    manager: ActorRef
  )(implicit ec: ExecutionContext, timeout: Timeout): LocalConsole =
    new LocalConsole(appVendor, appName, appVersion, manager, Vector.empty, enableStackTrace = false)

  def apply(
    appVendor: String,
    appName: String,
    appVersion: String,
    manager: ActorRef,
    enableStackTrace: Boolean
  )(implicit ec: ExecutionContext, timeout: Timeout): LocalConsole =
    new LocalConsole(appVendor, appName, appVersion, manager, Vector.empty, enableStackTrace)

  def apply(
    appVendor: String,
    appName: String,
    appVersion: String,
    manager: ActorRef,
    components: Vector[ComponentDescriptor]
  )(implicit ec: ExecutionContext, timeout: Timeout): LocalConsole =
    new LocalConsole(appVendor, appName, appVersion, manager, components, enableStackTrace = false)

  def apply(
    appVendor: String,
    appName: String,
    appVersion: String,
    manager: ActorRef,
    components: Vector[ComponentDescriptor],
    enableStackTrace: Boolean
  )(implicit ec: ExecutionContext, timeout: Timeout): LocalConsole =
    new LocalConsole(appVendor, appName, appVersion, manager, components, enableStackTrace)
}
