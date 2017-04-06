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

import javax.inject._

import akka.stream.Materializer
import core3.config.RuntimeConfig
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Play Filter for collecting HTTP request metrics.
  *
  * @param mat     implicit materializer
  * @param context implicit context
  */
class MetricsFilter @Inject()(implicit val mat: Materializer, context: ExecutionContext) extends Filter {
  private val logger = Logger("metrics")

  def apply(nextFilter: RequestHeader => Future[Result])
    (requestHeader: RequestHeader): Future[Result] = {
    val requestStart = System.currentTimeMillis()

    nextFilter(requestHeader).map {
      result =>
        if (RuntimeConfig.metricsCollectionEnabled) {
          val requestEnd = System.currentTimeMillis()
          val responseTime = requestEnd - requestStart
          val data = Json.obj(
            "created" -> requestEnd,
            "remoteAddress" -> requestHeader.remoteAddress,
            "responseTime" -> responseTime,
            "requestMethod" -> requestHeader.method,
            "requestURI" -> requestHeader.uri,
            "responseStatus" -> result.header.status,
            "responseContentType" -> result.body.contentType,
            "responseSize" -> result.body.contentLength
          )

          logger.info(s"$data")
        }

        result
    }
  }
}
