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
import com.typesafe.config.Config
import core3.config.StaticConfig
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.dals.{DatabaseAbstractionLayerComponent, LayerType}
import core3.database.{ContainerType, ObjectID}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A Database Abstraction Layer for sending data to Apache Solr.
  *
  * @constructor creates a new Solr DAL
  * @param hostname            the hostname of the Solr instance (clear and build only)
  * @param port                the port of the Solr instance (clear and build only)
  * @param schema              the schema to be used for HTTP connections ["http" OR "https"] (clear and build only)
  * @param username            the Solr user to be used when authenticating each request
  * @param password            the password for the Solr user
  * @param containerCompanions map with all registered container companion objects
  * @param shardsCount         number of shards to create for each new collection (passed directly to Solr)
  * @param replicasCount       number of replicas to create for each new collection (passed directly to Solr)
  * @param maxCommitTime       maximum number of seconds to wait before committing updates (passed directly to Solr)
  */
class Solr(
  private val hostname: String,
  private val port: Int,
  private val schema: String,
  private val username: String,
  private val password: String,
  private val containerCompanions: Map[ContainerType, SearchContainerCompanion],
  private val shardsCount: Int,
  private val replicasCount: Int,
  private val maxCommitTime: Int,
  ws: WSClient
)(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param containerCompanions map with all registered container companion objects
    * @param ws                  web service client
    * @param config              the config to use (if specified; default path is 'server.static.database.solr')
    * @return the new instance
    */
  def this(
    containerCompanions: Map[ContainerType, SearchContainerCompanion],
    ws: WSClient,
    config: Config = StaticConfig.get.getConfig("database.solr")
  )(implicit ec: ExecutionContext, timeout: Timeout) =
    this(
      config.getString("hostname"),
      config.getInt("port"),
      config.getString("schema"),
      config.getString("username"),
      config.getString("password"),
      containerCompanions,
      config.getInt("shardsCount"),
      config.getInt("replicasCount"),
      config.getInt("maxCommitTime"),
      ws
    )

  private val commitWithin = maxCommitTime * 1000
  private val numShards = shardsCount
  private val replicationFactor = replicasCount
  private val baseURL = s"$schema://$hostname:$port"

  //stats
  private var count_ExecuteAction: Long = 0
  private val count_GenericQuery: Long = 0
  private val count_CustomQuery: Long = 0
  private val count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  /**
    * Retrieves the search fields of the specified container type.
    *
    * @param objectType the container type
    * @return the requested search fields
    */
  private def getSearchFields(objectType: ContainerType): Map[String, String] = {
    assert(containerCompanions.contains(objectType))
    containerCompanions(objectType).getSearchFields
  }

  /**
    * Retrieves the search database (collection) name for the specified container type.
    *
    * @param objectType the container type
    * @return the requested collection name
    */
  private def getCollectionName(objectType: ContainerType): String = {
    assert(containerCompanions.contains(objectType))
    containerCompanions(objectType).getDatabaseName
  }

  /**
    * Retrieves the JSON representation of the supplied container
    *
    * @param container the container to be converted
    * @return the JSON data
    */
  private def getJSONDataFromContainer(container: Container): JsValue = {
    assert(containerCompanions.contains(container.objectType))
    val objectsCompanion = containerCompanions(container.objectType)
    val searchFields = objectsCompanion.getSearchFields.keys.toSeq
    val filteredFields = objectsCompanion.toJsonData(container).as[JsObject].fields.filter {
      case (k, _) =>
        searchFields.contains(k)

    }

    JsObject(filteredFields)
  }

  /**
    * Checks if the supplied response code matches the required response code and throws an exception, if it does not.
    *
    * @param response             the HTTP response to be checked
    * @param expectedResponseCode the expected response code
    * @param callerName           the name of the calling function
    * @return a <code>Future[Boolean]</code> holding the result of the check
    * @throws RuntimeException if the response codes do not match
    */
  private def checkResponse(response: WSResponse, expectedResponseCode: Int, callerName: String): Future[Boolean] = {
    if (response.status != expectedResponseCode) {
      Future.failed(new RuntimeException(s"core3.database.dals.json.Solr::$callerName > Unexpected response received (${response.status}): [${response.body}]"))
    } else {
      Future.successful(true)
    }
  }

  override protected def shutdown(): Unit = {}

  override protected def handle_GetDatabaseIdentifier: String = baseURL

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerCompanions.keys.toVector

  override protected def handle_GetLayerType: LayerType = LayerType.SolrSearch

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    Future.successful(true)
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    for {
      createResponse <- ws.url(s"$baseURL/solr/admin/collections")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withQueryString(
          "action" -> "CREATE",
          "name" -> getCollectionName(objectsType),
          "numShards" -> numShards.toString,
          "replicationFactor" -> replicationFactor.toString
        )
        .execute("POST")
      _ <- checkResponse(createResponse, 200, "handle_BuildDatabaseStructure")
      requestData <- Future.successful(
        Json.obj(
          "add-field" ->
            getSearchFields(objectsType).map {
              case (fieldName, fieldType) =>
                Map(
                  "name" -> fieldName,
                  "type" -> fieldType
                )
            }
        )
      )
      buildResponse <- ws.url(s"$baseURL/solr/${getCollectionName(objectsType)}/schema")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .post(requestData)
      _ <- checkResponse(buildResponse, 200, "handle_BuildDatabaseStructure")
    } yield {
      true
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    for {
      response <- ws.url(s"$baseURL/solr/admin/collections")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withQueryString(
          "action" -> "DELETE",
          "name" -> getCollectionName(objectsType)
        )
        .post("")
      _ <- checkResponse(response, 200, "handle_ClearDatabaseStructure")
    } yield {
      true
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
                )
              )
            )
          )
      }
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    Future.failed(new UnsupportedOperationException(s"core3.database.dals.json.SolrLayer::handle_GetGenericQueryResult > Cannot query Solr DAL."))
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    Future.failed(new UnsupportedOperationException(s"core3.database.dals.json.SolrLayer::handle_GetCustomQueryResult > Cannot query Solr DAL."))
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    Future.failed(new UnsupportedOperationException(s"core3.database.dals.json.SolrLayer::handle_GetObject > Cannot query Solr DAL."))
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    count_Create += 1

    val jsonData = Json.obj(
      "add" -> Json.obj(
        "commitWithin" -> commitWithin,
        "overwrite" -> false,
        "doc" -> getJSONDataFromContainer(container)
      )
    )

    for {
      response <- ws.url(s"$baseURL/solr/${getCollectionName(container.objectType)}/update/json")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .post(jsonData)
      _ <- checkResponse(response, 200, "handle_CreateObject")
    } yield {
      true
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    count_Update += 1

    val jsonData = Json.obj(
      "add" -> Json.obj(
        "commitWithin" -> commitWithin,
        "overwrite" -> true,
        "doc" -> getJSONDataFromContainer(container)
      )
    )

    for {
      response <- ws.url(s"$baseURL/solr/${getCollectionName(container.objectType)}/update/json")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .post(jsonData)
      _ <- checkResponse(response, 200, "handle_UpdateObject")
    } yield {
      true
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    count_Delete += 1

    val jsonData = Json.obj("delete" -> Json.obj("id" -> objectID))

    for {
      container <- handle_GetObject(objectType, objectID)
      _ <- Future {
        if(!container.isInstanceOf[MutableContainer]) {
          throw new IllegalStateException(s"core3.database.dals.json.Solr::handle_DeleteObject > Objects of type [$objectType] cannot be deleted.")
        }
      }
      response <- ws.url(s"$baseURL/solr/${getCollectionName(objectType)}/update/json")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .post(jsonData)
      _ <- checkResponse(response, 200, "handle_DeleteObject")
    } yield {
      true
    }
  }
}

object Solr extends ComponentCompanion {
  def props(
    hostname: String,
    port: Int,
    schema: String,
    solrUser: String,
    solrUserPassword: String,
    containerCompanions: Map[ContainerType, SearchContainerCompanion],
    shardsCount: Int,
    replicasCount: Int,
    maxCommitTime: Int,
    ws: WSClient
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[Solr],
    hostname,
    port,
    schema,
    solrUser,
    solrUserPassword,
    containerCompanions,
    shardsCount,
    replicasCount,
    maxCommitTime,
    ws,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, SearchContainerCompanion],
    ws: WSClient,
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[Solr],
    containerCompanions,
    ws,
    config,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, SearchContainerCompanion],
    ws: WSClient
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[Solr],
    containerCompanions,
    ws,
    StaticConfig.get.getConfig("database.solr"),
    ec,
    timeout
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
