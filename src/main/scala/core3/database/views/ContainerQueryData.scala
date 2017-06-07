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
package core3.database.views

import core3.database.ContainerType
import core3.database.containers.Container

sealed trait QueryType

object QueryType {

  case object Generic extends QueryType

  case object Custom extends QueryType

}

/**
  * Class for storing container query configuration data.
  *
  * @param queryType       the query type to be performed
  * @param containerType   the container type to query
  * @param customQueryName the name of a custom query to execute (required, if query type is 'Custom')
  * @param queryParams     the parameters to use for the query (required, if query type is 'Custom')
  * @param withFilter      a function to be used for filtering the query results
  */
case class ContainerQueryData(
  queryType: QueryType,
  containerType: ContainerType,
  customQueryName: Option[String],
  queryParams: Option[Map[String, String]],
  withFilter: Option[(Container) => Boolean]) {
}

object ContainerQueryData {
  def apply(containerType: ContainerType): ContainerQueryData =
    new ContainerQueryData(queryType = QueryType.Generic, containerType, customQueryName = None, queryParams = None, withFilter = None)

  def apply(containerType: ContainerType, customQueryName: String, queryParams: Map[String, String]): ContainerQueryData =
    new ContainerQueryData(queryType = QueryType.Custom, containerType, Some(customQueryName), Some(queryParams), withFilter = None)

  def apply(containerType: ContainerType, withFilter: (Container) => Boolean): ContainerQueryData =
    new ContainerQueryData(queryType = QueryType.Generic, containerType, customQueryName = None, queryParams = None, Some(withFilter))

  def apply(containerType: ContainerType, customQueryName: String, queryParams: Map[String, String], withFilter: (Container) => Boolean): ContainerQueryData =
    new ContainerQueryData(queryType = QueryType.Custom, containerType, Some(customQueryName), Some(queryParams), Some(withFilter))
}
