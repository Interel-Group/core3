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
package core3.database.dals.json

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticsearchClientUri, TcpClient}
import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.dals.{DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.common.settings.Settings
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A Database Abstraction Layer for sending search data to ElasticSearch.
  *
  * Notes:
  * - Even if ES is deployed as a multi-node cluster, connections should be created for the local node.
  * - If 'searchOnly' is set to false, full container data will be stored; great care must be taken when
  * storing sensitive data.
  * - If 'coexist' is set to true and:
  *     - if 'searchOnly' is set to true, the 'clear' and 'build' operations will do nothing
  *     - if 'searchOnly' is set to false, the 'clear' and 'build' operations will work as normal
  * - If 'coexist' is set to false, the 'clear' and 'build' operations will work as normal, which will cause
  * conflicts when a search and a store instance are used for the same container.
  * - Refresh policies:
  *     - NONE -> controlled by ES (default)
  *     - IMMEDIATE -> perform refresh immediately and wait for completion before getting response
  *     - WAIT_UNTIL -> wait until next refresh before getting response
  *
  * @param hostname            the hostname for the ElasticSearch instance
  * @param port                the port for the instance
  * @param clusterName         the name of the ElasticSearch cluster
  * @param containerDefinitions all configured container definitions
  * @param searchOnly          set to false to enable using ES as a data store, instead of just search
  * @param coexist             set to true to enable the use of ES as both data and search store
  * @param refreshPolicy       the refresh policy to use when making updates
  * @param scrollSize          the number of elements to retrieve with each scroll request
  * @param scrollExpiration    scroll context expiration (in seconds)
  */
class ElasticSearch(
  private val hostname: String,
  private val port: Int,
  private val clusterName: String,
  private val containerDefinitions: ContainerDefinitions[BasicContainerDefinition with JsonContainerDefinition],
  private val searchOnly: Boolean,
  private val coexist: Boolean,
  private val refreshPolicy: RefreshPolicy,
  private val scrollSize: Int,
  private val scrollExpiration: Int
)(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param containerDefinitions all configured container definitions
    * @param config              the config to use (if specified; default path is 'server.static.database.elastic')
    * @return the new instance
    */
  def this(
            containerDefinitions: ContainerDefinitions[BasicContainerDefinition with JsonContainerDefinition],
            config: Config = StaticConfig.get.getConfig("database.elastic")
  )(implicit ec: ExecutionContext, timeout: Timeout) =
    this(
      config.getString("hostname"),
      config.getInt("port"),
      config.getString("clusterName"),
      containerDefinitions,
      config.getBoolean("searchOnly"),
      config.getBoolean("coexist"),
      config.getString("refreshPolicy").toLowerCase match {
        case "none" => RefreshPolicy.NONE
        case "immediate" => RefreshPolicy.IMMEDIATE
        case "wait_until" => RefreshPolicy.WAIT_UNTIL
        case policy => throw new IllegalArgumentException(s"core3.database.dals.json.ElasticSearch::() > Invalid refresh policy configured: [$policy].")
      },
      config.getInt("scrollSize"),
      config.getInt("scrollExpiration")
    )

  private val scrollKeepAlive = s"${scrollExpiration}s"
  private val serviceSettings = Settings.builder().put("cluster.name", clusterName).build()
  private val service = ElasticsearchClientUri(hostname, port)
  private val client = TcpClient.transport(serviceSettings, service)
  private val (docType: String, layerType: LayerType) =
    if (!searchOnly) {
      ("store", LayerType.ElasticStore)
    } else {
      ("search", LayerType.ElasticSearch)
    }

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  override protected def handle_GetDatabaseIdentifier: String = service.uri

  override protected def handle_GetLayerType: LayerType = layerType

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerDefinitions.supportedContainers

  override def shutdown(): Unit = {
    client.close()
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    val indexName = containerDefinitions(objectsType).getDatabaseName
    client.execute(indexExists(indexName)).map(_.isExists)
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    val objectsCompanion = containerDefinitions(objectsType)
    val indexName = objectsCompanion.getDatabaseName

    if (coexist && searchOnly) {
      //skips the operation, when the search-only instance is expected to coexist with another one
      Future.successful(true)
    } else {
      (for {
        indexExists <- client.execute(indexExists(indexName)).map(_.isExists)
        indexExists <- if (!indexExists) {
          client.execute(createIndex(indexName).shards(3).replicas(2)).map(_.isShardsAcked)
        } else {
          Future.successful(true)
        }
      } yield {
        if (indexExists) {
          if (searchOnly) {
            client.execute {
              putMapping(indexName / docType) as {
                objectsCompanion.asInstanceOf[SearchContainerDefinition].getSearchFields.map {
                  case (name, fieldType) =>
                    fieldType match {
                      case "string" => textField(name)
                      case "text" => textField(name)
                      case "boolean" => booleanField(name)
                      case "date" => dateField(name)
                      case "integer" => intField(name)
                      case "long" => longField(name)
                      case "double" => doubleField(name)
                      case "float" => floatField(name)
                      case "nested" => nestedField(name)
                      case "object" => objectField(name)
                      case _ => throw new RuntimeException(s"core3.database.dals.json.ElasticSearch::handle_BuildDatabaseStructure > " +
                        s"Cannot create field [$name] with type [$fieldType] for object type [$objectsType]; type is not supported.")
                    }
                }
              }
            }.map(
              response =>
                response.isAcknowledged
            )
          } else {
            Future.successful(true)
          }
        } else {
          Future.failed(new RuntimeException(s"core3.database.dals.json.ElasticSearch::handle_BuildDatabaseStructure > " +
            s"Failed to build database for object type [$objectsType]; index [$indexName] could not be created."))
        }
      }).flatMap(identity)
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (coexist && searchOnly) {
      //skips the operation, when the search-only instance is expected to coexist with another one
      Future.successful(true)
    } else {
      val objectsCompanion = containerDefinitions(objectsType)
      val indexName = objectsCompanion.getDatabaseName

      client.execute(indexExists(indexName)).flatMap {
        response =>
          if (response.isExists) {
            client.execute(deleteIndex(indexName)).map(_.isAcknowledged)
          } else {
            Future.failed(new RuntimeException(s"core3.database.dals.json.ElasticSearch::handle_ClearDatabaseStructure > " +
              s"Failed to clear database for object type [$objectsType]; index [$indexName] was not found."))
          }
      }
    }
  }

  private def processScroll(
    currentScrollId: String,
    previousData: Array[String]
  ): Future[Vector[String]] = {
    if(previousData.length >= scrollSize) {
      client.execute {
        searchScroll(currentScrollId, scrollKeepAlive)
      }.flatMap {
        response =>
          if (response.hits.nonEmpty) {
            processScroll(response.scrollId, response.hits.map(_.sourceAsString) ++ previousData)
          } else {
            Future.successful(previousData.toVector)
          }
      }
    } else {
      Future.successful(previousData.toVector)
    }
  }

  /**
    * Retrieves all containers from the database.
    *
    * @param objectsType the database to query
    * @param companion   JSON companion object for the specified object type
    * @return the retrieved containers
    */
  private def getAllContainers(objectsType: ContainerType, companion: BasicContainerDefinition with JsonContainerDefinition): Future[Vector[Container]] = {
    val indexName = companion.getDatabaseName

    for {
      initialResponse <- client.execute {
        search(indexName / docType).matchAllQuery().size(scrollSize).scroll(scrollKeepAlive)
      }
      data <- processScroll(initialResponse.scrollId, initialResponse.hits.map(_.sourceAsString))
    } yield {
      data.map {
        c =>
          companion.fromJsonData(Json.parse(c))
      }
    }
  }

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future.successful {
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "layerType" -> handle_GetLayerType,
                "supportedContainers" -> handle_GetSupportedContainers,
                "id" -> handle_GetDatabaseIdentifier,
                "counters" -> Json.obj(
                  "executeAction" -> count_ExecuteAction,
                  "genericQuery" -> count_GenericQuery,
                  "customQuery" -> count_CustomQuery,
                  "get" -> count_Get,
                  "create" -> count_Create,
                  "update" -> count_Update,
                  "delete" -> count_Delete
                ),
                "settings" -> Json.obj(
                  "searchOnly" -> searchOnly,
                  "docType" -> docType,
                  "coexist" -> coexist,
                  "refreshPolicy" -> refreshPolicy.toString,
                  "clusterName" -> clusterName
                )
              )
            )
          )
      }
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    if (!searchOnly) {
      count_GenericQuery += 1

      val companion = containerDefinitions(objectsType)

      getAllContainers(objectsType, companion)
    } else {
      Future.failed(new UnsupportedOperationException(s"core3.database.dals.json.ElasticSearch::handle_GetGenericQueryResult > " +
        s"Cannot query ElasticSearch DAL while running in 'searchOnly' mode."))
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    if (!searchOnly) {
      count_CustomQuery += 1

      val companion = containerDefinitions(objectsType)

      getAllContainers(objectsType, companion).map {
        containers =>
          containers.filter {
            current =>
              companion.matchCustomQuery(customQueryName, queryParams, current)
          }
      }
    } else {
      Future.failed(
        new UnsupportedOperationException(s"core3.database.dals.json.ElasticSearch::handle_GetCustomQueryResult > " +
          s"Cannot query ElasticSearch DAL while running in 'searchOnly' mode.")
      )
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    if (!searchOnly) {
      count_Get += 1

      val objectsCompanion = containerDefinitions(objectType)
      val indexName = objectsCompanion.getDatabaseName

      for {
        response <- client.execute {
          get(objectID.toString).from(indexName / docType)
        }
      } yield {
        objectsCompanion.fromJsonData(Json.parse(response.sourceAsString))
      }
    } else {
      Future.failed(new UnsupportedOperationException(s"core3.database.dals.json.ElasticSearch::handle_GetObject > " +
        s"Cannot query ElasticSearch DAL while running in 'searchOnly' mode."))
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    count_Create += 1

    val objectsCompanion = containerDefinitions(container.objectType)
    val indexName = objectsCompanion.getDatabaseName

    for {
      data <- Future {
        val jsonContainer = objectsCompanion.toJsonData(container)

        if(searchOnly) {
          val searchFields = objectsCompanion.asInstanceOf[SearchContainerDefinition].getSearchFields.keys.toSeq

          val filteredFields = jsonContainer.as[JsObject].fields.filter {
            case (k, _) =>
              searchFields.contains(k)
          }

          JsObject(filteredFields)
        } else {
          jsonContainer
        }
      }
      response <- client.execute {
        indexInto(indexName, docType)
          .doc(Json.stringify(data))
          .withId(container.id.toString)
          .createOnly(createOnly = true)
          .refresh(refreshPolicy)
      }
    } yield {
      response.created
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    count_Update += 1

    val objectsCompanion = containerDefinitions(container.objectType)
    val indexName = objectsCompanion.getDatabaseName

    for {
      data <- Future {
        val jsonContainer = objectsCompanion.toJsonData(container)

        if(searchOnly) {
          val searchFields = objectsCompanion.asInstanceOf[SearchContainerDefinition].getSearchFields.keys.toSeq

          val filteredFields = jsonContainer.as[JsObject].fields.filter {
            case (k, _) =>
              searchFields.contains(k)
          }

          JsObject(filteredFields)
        } else {
          jsonContainer
        }
      }
      response <- client.execute {
        update(container.id.toString)
          .in(indexName / docType)
          .doc(Json.stringify(data))
          .refresh(refreshPolicy)
      }
    } yield {
      response.result == DocWriteResponse.Result.UPDATED
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    count_Delete += 1

    val objectsCompanion =  containerDefinitions(objectType)

    for {
      _ <- if(!searchOnly) {
        handle_GetObject(objectType, objectID).map {
          container =>
            if(!container.isInstanceOf[MutableContainer]) {
              throw new IllegalStateException(s"core3.database.dals.json.ElasticSearch::handle_DeleteObject > Objects of type [$objectType] cannot be deleted.")
            }
        }
      } else {
        //cannot load and check object when in search-only mode
        Future.successful(())
      }
      response <- client.execute {
        delete(objectID.toString)
          .from(objectsCompanion.getDatabaseName / docType)
          .refresh(refreshPolicy)
      }
    } yield {
      response.getResult == DocWriteResponse.Result.DELETED
    }
  }
}

object ElasticSearch extends ComponentCompanion {
  def props(
    hostname: String,
    port: Int,
    clusterName: String,
    containerDefinitions: ContainerDefinitions[BasicContainerDefinition with JsonContainerDefinition],
    searchOnly: Boolean = true,
    coexist: Boolean = false,
    refreshPolicy: RefreshPolicy = RefreshPolicy.NONE,
    scrollSize: Int,
    scrollExpiration: Int
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[ElasticSearch],
    hostname,
    port,
    clusterName,
    containerDefinitions,
    searchOnly,
    coexist,
    refreshPolicy,
    scrollSize,
    scrollExpiration,
    ec,
    timeout
  )

  def props(
    containerDefinitions: ContainerDefinitions[BasicContainerDefinition with JsonContainerDefinition],
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[ElasticSearch],
    containerDefinitions,
    config,
    ec,
    timeout
  )

  def props(
    containerDefinitions: ContainerDefinitions[BasicContainerDefinition with JsonContainerDefinition]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[ElasticSearch],
    containerDefinitions,
    StaticConfig.get.getConfig("database.elastic"),
    ec,
    timeout
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
