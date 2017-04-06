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
package core3.http.filters

import javax.inject.Inject

import akka.stream.Materializer
import core3.config.RuntimeConfig
import core3.http.handlers
import play.api.Logger
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Play Filter for handling system maintenance mode, when enabled.
  *
  * @param unaffectedURIs        the URIs which are to remain functional when maintenance mode is enabled
  * @param unavailableHandlerOpt custom response handler; used when maintenance mode is enabled (if not set, the system's default handler will be used)
  */
class MaintenanceModeFilter @Inject()(unaffectedURIs: Vector[String], unavailableHandlerOpt: Option[() => Future[Result]] = None)
  (implicit val mat: Materializer, context: ExecutionContext) extends Filter {
  private val logger = Logger("audit")

  def apply(nextFilter: RequestHeader => Future[Result])
    (requestHeader: RequestHeader): Future[Result] = {
    if (RuntimeConfig.maintenanceModeEnabled) {
      if (unaffectedURIs.contains(requestHeader.uri)) {
        logger.info(s"core3.http.filters.MaintenanceModeFilter::apply > Allowed [${requestHeader.method}] request " +
          s"for URI [${requestHeader.uri}] from [${requestHeader.remoteAddress}] while maintenance mode is enabled.")

        nextFilter(requestHeader)
      } else {
        logger.warn(s"core3.http.filters.MaintenanceModeFilter::apply > Blocked [${requestHeader.method}] request " +
          s"for URI [${requestHeader.uri}] from [${requestHeader.remoteAddress}] while maintenance mode is enabled.")

        unavailableHandlerOpt match {
          case Some(handler) => handler()
          case None => handlers.Default.unavailable()
        }
      }
    } else {
      nextFilter(requestHeader)
    }
  }
}
