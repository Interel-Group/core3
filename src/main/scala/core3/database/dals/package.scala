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
package core3.database

import play.api.libs.json.{Reads, Writes, JsString}

package object dals {

  sealed trait LayerType

  object LayerType {

    case object Core extends LayerType

    case object SlickDB extends LayerType

    case object CouchDB extends LayerType

    case object SolrSearch extends LayerType

    case object MemoryOnlyDB extends LayerType

    case object Redis extends LayerType

    case object ElasticSearch extends LayerType

    case object ElasticStore extends LayerType

    case object DistributedCache extends LayerType

    def fromString(value: String): LayerType = {
      value match {
        case "Core" => LayerType.Core
        case "SlickDB" => LayerType.SlickDB
        case "CouchDB" => LayerType.CouchDB
        case "SolrSearch" => LayerType.SolrSearch
        case "MemoryOnlyDB" => LayerType.MemoryOnlyDB
        case "Redis" => LayerType.Redis
        case "ElasticSearch" => LayerType.ElasticSearch
        case "ElasticStore" => LayerType.ElasticStore
        case "DistributedCache" => LayerType.DistributedCache
      }
    }
  }

  implicit val layerTypeReads: Reads[LayerType] = Reads {
    json =>
      json.validate[String].map(LayerType.fromString)
  }

  implicit val layerTypeWrites: Writes[LayerType] = Writes {
    layerType =>
      JsString(layerType.toString)
  }
}
