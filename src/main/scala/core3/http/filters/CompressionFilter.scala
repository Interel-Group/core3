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
import play.filters.gzip.GzipFilter

/**
  * Play Filter for performing gzip compression.
  *
  * Compression is performed if the response matches one of the specified content types and if
  * the response content size is above the specified minimum size. If no content types are
  * specified, compression is performed on all responses.
  *
  * @param contentTypes   the type of responses to perform compression on (or all, if empty)
  * @param minContentSize the minimum response content size for compression
  * @param mat            implicit materializer
  */
class CompressionFilter @Inject()(contentTypes: Vector[String], minContentSize: Long)
  (implicit val mat: Materializer) extends GzipFilter(
  shouldGzip = (_, response) => {
    ((contentTypes.isEmpty || response.body.contentType.exists { c => contentTypes.exists(c.startsWith) })
      && response.body.contentLength.getOrElse(0L) > minContentSize)
  }) {
}
