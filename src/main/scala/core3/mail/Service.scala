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
package core3.mail

import javax.activation.{DataHandler, FileDataSource}
import javax.mail.internet.{InternetAddress, MimeBodyPart}

import akka.actor.Props
import akka.pattern.pipe
import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.{Component, ComponentCompanion}
import courier._
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * A service for sending emails.
  *
  * Note: Always uses TLS.
  *
  * @param hostname email server hostname
  * @param port     email server port
  * @param username the user to authenticate with
  * @param password the password associated with the user
  */
class Service(
  private val hostname: String,
  private val port: Int,
  private val username: String,
  private val password: String
)(implicit ec: ExecutionContext) extends Component {

  import Service._

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param config the config to use (if specified; default path is 'server.static.mail')
    * @return the new instance
    */
  def this(
    config: Config = StaticConfig.get.getConfig("mail")
  )(implicit ec: ExecutionContext) =
    this(
      config.getString("hostname"),
      config.getInt("port"),
      config.getString("username"),
      config.getString("password")
    )

  private val auditLogger = Logger("audit")
  private val mailer = Mailer(hostname, port)
    .auth(true)
    .as(username, password)
    .startTtls(true)()

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_Mailed: Long = 0
  private var count_Failed: Long = 0

  override protected def shutdown(): Unit = {}

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future {
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "id" -> s"$hostname:$port",
                "counters" -> Json.obj(
                  "executeAction" -> count_ExecuteAction,
                  "mailed" -> count_Mailed,
                  "failed" -> count_Failed
                )
              )
            )
          )
      }
    }
  }

  private def handle_send(
    from: String,
    to: Vector[String],
    subject: String,
    htmlBody: String,
    attachments: Vector[java.io.File],
    inlineAttachments: Vector[(String, String)]
  ): Future[Unit] = {
    if (from.isEmpty) throw new IllegalArgumentException(s"core3.mail.Core::send > Empty 'from' field supplied.")
    if (to.isEmpty || to.forall(_.isEmpty)) throw new IllegalArgumentException(s"core3.mail.Core::send > Empty 'to' field supplied.")
    if (subject.isEmpty) throw new IllegalArgumentException(s"core3.mail.Core::send > Empty 'subject' field supplied.")
    if (htmlBody.isEmpty) throw new IllegalArgumentException(s"core3.mail.Core::send > Empty 'htmlBody' field supplied.")

    val content = Multipart().html(htmlBody)

    attachments.foreach(content.attach(_))

    inlineAttachments.foreach {
      case (attachmentPath, attachmentName) =>
        val part = new MimeBodyPart()
        part.setDataHandler(new DataHandler(new FileDataSource(attachmentPath)))
        part.setHeader("Content-ID", attachmentName)
        content.add(part)
    }

    val envelope = Envelope
      .from(new InternetAddress(from))
      .to(to.map(new InternetAddress(_)): _*)
      .subject(subject)
      .content(content)

    handle_send(envelope)
  }

  private def handle_send(envelope: Envelope): Future[Unit] = {
    count_Mailed += 1
    val result = mailer(envelope)
    result.onComplete {
      case Success(_) =>
        auditLogger.info(s"core3.database.dals.Core::send > Successfully sent email from [${envelope.from}] to [${envelope._to.mkString(", ")}] with subject [${envelope._subject}].")
      case Failure(e) =>
        count_Failed += 1
        auditLogger.error(s"core3.database.dals.Core::send > Exception [${e.getMessage}] encountered while sending email " +
          s"from [${envelope.from}] to [${envelope._to.mkString(", ")}] with subject [[${envelope._subject}].", e)
    }

    result
  }

  addReceiver {
    case SendMessage(from, to, subject, htmlBody, attachments, inlineAttachments) =>
      try {
        handle_send(from, to, subject, htmlBody, attachments, inlineAttachments) pipeTo sender
      } catch {
        case e: IllegalArgumentException => Future.failed(e) pipeTo sender
      }

    case SendPreBuiltMessage(envelope) => handle_send(envelope)
  }
}

object Service extends ComponentCompanion {

  /**
    * Sends an email to multiple recipients with the supplied parameters.
    *
    * @param from              'from' email address
    * @param to                list of 'to' email addresses
    * @param subject           email subject
    * @param htmlBody          email body
    * @param attachments       a list of regular (non-inline) attachments
    * @param inlineAttachments a list of (full path, CID) inline attachments
    * @return Future[Unit] - nothing
    */
  case class SendMessage(
    from: String,
    to: Vector[String],
    subject: String,
    htmlBody: String,
    attachments: Vector[java.io.File] = Vector.empty,
    inlineAttachments: Vector[(String, String)] = Vector.empty
  )

  /**
    * Sends an email defined by the supplied envelope object.
    *
    * @param envelope the email to be sent
    * @return Future[Unit] - nothing
    */
  case class SendPreBuiltMessage(envelope: Envelope)

  def props(hostname: String, port: Int, user: String, password: String)(implicit ec: ExecutionContext): Props = Props(
    classOf[Service], hostname, port, user, password, ec
  )

  def props(config: Config)(implicit ec: ExecutionContext): Props = Props(
    classOf[Service], config, ec
  )

  def props()(implicit ec: ExecutionContext): Props = Props(
    classOf[Service], ec
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
