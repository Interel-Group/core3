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

import core3.core.Component.{ActionDescriptor, ComponentDescriptor}
import scopt.OptionParser

/**
  * Terminal command parser.
  *
  * Notes:
  * - Default components - help, system, qq(quit)
  * - Additional components are added via [[withComponent]]
  *
  * @param appVendor  the name of the app vendor
  * @param appName    the name of the app
  * @param appVersion the current app version
  */
class CommandParser(appVendor: String, appName: String, appVersion: String) {

  import CommandParser._

  private var componentAutoCompleteData = Map[String, Vector[ActionDescriptor]](
    "help" -> Vector.empty,
    "system" -> Vector(
      ActionDescriptor(name = "get", description = "", arguments = Some(systemGetArgs.map(_ -> "").toMap)),
      ActionDescriptor(name = "reload", description = "", arguments = Some(systemReloadArgs.map(_ -> "").toMap)),
      ActionDescriptor(name = "enable", description = "", arguments = Some(systemModeArgs.map(_ -> "").toMap)),
      ActionDescriptor(name = "disable", description = "", arguments = Some(systemModeArgs.map(_ -> "").toMap))
    )
  )

  //builds the internal parser and creates the default components
  private val parser = new OptionParser[CommandConfig]("") {
    head(s"$appVendor::$appName - $appVersion")

    help("help").abbr("h").text("Shows this message").action((_, config) => config.copy(component = Some("help")))
    version("version").abbr("v").text("Shows the application version\n").action((_, config) => config.copy(component = Some("version")))

    opt[Unit]('q', "quit")
      .text("Exits the CLI and, if running a local application instance with --enable-console, terminates the application.\n")
      .action((_, config) => config.copy(component = Some("exit")))

    cmd("qq").hidden().action((_, config) => config.copy(component = Some("exit")))

    cmd("help")
      .text("Shows help/usage info for the whole system or for a component and/or action\n")
      .action((_, config) => config.copy(component = Some("help")))
      .children(
        arg[String]("<component>")
          .optional()
          .text("The component for which to show help info\n")
          .action((component, config) => config.copy(action = Some(component)))
          .children(
            arg[String]("<action>")
              .optional()
              .text("The component's action for which to show help info\n")
              .action((action, config) => config.copy(action = Some(s"${config.action.get} $action")))
          )
      )

    cmd("system")
      .text("Sends an action to the core system component\n")
      .action((_, config) => config.copy(component = Some("system")))
      .children(
        cmd("get").text("Retrieves information about the current system state\n")
          .children(
            arg[String]("<info type>")
              .required()
              .text(s"Type of information to retrieve: [${systemGetArgs.mkString("|")}]\n")
              .validate(value => if (systemGetArgs.contains(value)) success else failure(s"Argument must be one of [${systemGetArgs.mkString(", ")}]"))
              .action((value, config) => config.copy(action = Some(s"get_$value")))
          ),

        cmd("reload").text("Reloads the specified configuration\n")
          .children(
            arg[String]("<configuration type>")
              .required()
              .text(s"Type of configuration to reload: [${systemReloadArgs.mkString("|")}]\n")
              .validate(value => if (systemReloadArgs.contains(value)) success else failure(s"Argument must be one of [${systemReloadArgs.mkString(", ")}]"))
              .action((value, config) => config.copy(action = Some(s"reload_$value")))
          ),

        cmd("enable").text("Enables a system mode\n")
          .children(
            arg[String]("<mode>")
              .required()
              .text(s"Modes to enable: [${systemModeArgs.mkString("|")}]\n")
              .validate(value => if (systemModeArgs.contains(value)) success else failure(s"Argument must be one of [${systemModeArgs.mkString(", ")}]"))
              .action((value, config) => config.copy(action = Some(s"${value}_enable")))
          ),

        cmd("disable").text("Disables a system mode\n")
          .children(
            arg[String]("<mode>")
              .required()
              .text(s"Modes to disable: [${systemModeArgs.mkString("|")}]\n")
              .validate(value => if (systemModeArgs.contains(value)) success else failure(s"Argument must be one of [${systemModeArgs.mkString(", ")}]"))
              .action((value, config) => config.copy(action = Some(s"${value}_disable")))
          )
      )

    checkConfig {
      config =>
        (config.component, config.action) match {
          case (Some(_), Some(_)) => success
          case (None, Some(_)) => failure("Missing argument <component>")
          case (Some(component), None) => component match {
            case "help" => success
            case "version" => success
            case "exit" => success
            case _ => failure("Missing argument <action>")
          }
          case (None, None) => failure("Missing arguments <component> and <action>")
        }
    }

    //disables termination on version/help
    override def terminate(exitState: Either[String, Unit]): Unit = {}

    //disables default print to console
    override def showUsage(): Unit = {}
  }

  /**
    * Adds a new component to the parser.
    *
    * @param component the component descriptor to be used
    */
  def withComponent(component: ComponentDescriptor): Unit = {
    val newCmdChildren = component.companion.getActionDescriptors.map {
      current =>
        val childArguments = current.arguments.getOrElse(Map.empty).map {
          case (argument, argumentDescription) =>
            parser.arg[String](s"<$argument>").required().text(s"$argumentDescription\n").action {
              (value, config) =>
                config.copy(
                  params = Some(
                    config.params match {
                      case Some(params) => params + (argument -> Some(value))
                      case None => Map(argument -> Some(value))
                    }
                  )
                )
            }
        }

        parser.cmd(current.name)
          .text(s"${current.description}\n")
          .action((_, config) => config.copy(action = Some(current.name)))
          .children(childArguments.toSeq: _*)
    }

    parser.cmd(component.name)
      .text(s"${component.description}\n")
      .action((_, config) => config.copy(component = Some(component.name)))
      .children(newCmdChildren: _*)

    componentAutoCompleteData += (component.name -> component.companion.getActionDescriptors)
  }

  /**
    * Parses the supplied raw input string.
    *
    * @param rawInput the string to parse
    * @param config   the pre-built config to use (if any)
    * @return the parsed command configuration
    */
  def parse(rawInput: String, config: CommandConfig = CommandConfig()): Option[CommandConfig] = {
    parser.parse(rawInput.split(" "), config)
  }

  /**
    * Parses the supplied sequence of input parameters.
    *
    * @param input  the input to parse
    * @param config the pre-build config to use
    * @return the parsed command configuration
    */
  def parse(input: Vector[String], config: CommandConfig): Option[CommandConfig] = {
    parser.parse(input, config)
  }

  /**
    * Retrieves the help/usage message.
    *
    * @param withCommandFilter the command to filter the usage/help by (if any)
    * @return the requested usage message
    */
  def usageMessage(withCommandFilter: Option[String] = None): String = {
    val message = withCommandFilter match {
      case Some(filter) =>
        s"""(?s)(Command: $filter.+?)Command: (?!$filter)""".r.findFirstMatchIn(parser.usage)
          .orElse(s"""(?s)(Command: $filter.+?)\\Z""".r.findFirstMatchIn(parser.usage))
          .map(result => result.group(1))
          .getOrElse(s"No help/usage found for [$filter]")

      case None => parser.usage
    }

    message.trim
  }

  /**
    * Retrieves the auto-complete data associated with the parser.
    *
    * @return the requested data
    */
  def getComponentAutoCompleteData: Map[String, Vector[ActionDescriptor]] = componentAutoCompleteData
}

object CommandParser {
  private val systemGetArgs: Vector[String] = Vector("modes", "components", "static_config", "dynamic_config")
  private val systemReloadArgs: Vector[String] = Vector("dynamic_config")
  private val systemModeArgs: Vector[String] = Vector("maintenance", "metrics", "trace")

  def apply(appVendor: String, appName: String, appVersion: String): CommandParser = new CommandParser(appVendor, appName, appVersion)

  def apply(appVendor: String, appName: String, appVersion: String, components: Vector[ComponentDescriptor]): CommandParser = {
    val parser = new CommandParser(appVendor, appName, appVersion)
    components.foreach(parser.withComponent)
    parser
  }
}
