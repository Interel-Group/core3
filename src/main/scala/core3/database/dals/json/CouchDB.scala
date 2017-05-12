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
  * A Database Abstraction Layer for accessing a CouchDB database.
  *
  * Note: When the DAL is used for caching, partial containers are written to the databases
  * and the DAL cannot be used for reading.
  *
  * @constructor creates a new CouchDB DAL
  * @param hostname            the hostname of the CouchDB instance
  * @param port                the port of the CouchDB instance
  * @param schema              the schema to be used for HTTP connections ["http" OR "https"]
  * @param username            the DB user to be used when authenticating each request
  * @param password            the password for the DB user
  * @param containerCompanions map with all registered container companion objects
  * @param ws                  web service client
  */
class CouchDB(
  private val hostname: String,
  private val port: Int,
  private val schema: String,
  private val username: String,
  private val password: String,
  private val containerCompanions: Map[ContainerType, JSONContainerCompanion],
  ws: WSClient
)(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  /**
    * Creates a new instance with the supplied config or uses the default config location.
    *
    * @param containerCompanions map with all registered container companion objects
    * @param ws                  web service client
    * @param config              the config to use (if specified; default path is 'server.static.database.couchdb')
    * @return the new instance
    */
  def this(
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    ws: WSClient,
    config: Config = StaticConfig.get.getConfig("database.couchdb")
  )(implicit ec: ExecutionContext, timeout: Timeout) =
    this(
      config.getString("hostname"),
      config.getInt("port"),
      config.getString("schema"),
      config.getString("username"),
      config.getString("password"),
      containerCompanions,
      ws
    )

  private val baseURL = s"$schema://$hostname:$port"
  private val queryDesignDocName = "workflows_query"
  private val queryViewName = "get_all"
  private val queryDesignDocMapFunction = "function(doc) {emit(doc._id, doc);}"

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  private val additionalViews: Map[ContainerType, Vector[String]] = Map()

  /**
    * Creates a CouchDB URL for the specified container type's database.
    *
    * @param objectType the container type of the DB
    * @return the generated URL
    */
  private def getDatabaseURLFromType(objectType: ContainerType): String = {
    assert(containerCompanions.contains(objectType))
    baseURL + "/" + containerCompanions(objectType).getDatabaseName
  }

  /**
    * Creates a CouchDB URL for the specified document.
    *
    * @param objectType the type of the document
    * @param id         the ID of the document
    * @return the generated URL
    */
  private def getRequestURLFromType(objectType: ContainerType, id: ObjectID): String = {
    getDatabaseURLFromType(objectType) + "/" + id
  }

  /**
    * Checks if the supplied response code matches one of the required response codes and throws an exception, if it does not.
    *
    * @param response              the HTTP response to be checked
    * @param expectedResponseCodes a sequence of expected response codes
    * @param callerName            the name of the calling function
    * @return a <code>Future[Boolean]</code> holding the result of the check
    * @throws RuntimeException if the response codes do not match
    */
  private def checkResponse(response: WSResponse, expectedResponseCodes: Seq[Int], callerName: String): Future[Boolean] = {
    if (!expectedResponseCodes.contains(response.status)) {
      Future.failed(
        new RuntimeException(
          s"core3.database.dals.json.CouchDB::$callerName > Unexpected response received (${response.status}): [${response.body}]"
        )
      )
    } else {
      Future.successful(true)
    }
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
    checkResponse(response, Seq(expectedResponseCode), callerName)
  }

  /**
    * Retrieves the CouchDB revision ID of the specified document.
    *
    * @param objectType the document type
    * @param objectID   the ID of the document
    * @return the requested revision ID
    */
  private def getRevisionID(objectType: ContainerType, objectID: ObjectID): Future[String] = {
    for {
      response <- ws.url(getRequestURLFromType(objectType, objectID))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .get()
      _ <- checkResponse(response, 200, "getRevisionID")
    } yield {
      (response.json \ "_rev").get.as[String]
    }
  }

  /**
    * Converts the supplied JSON data to a container.
    *
    * @param objectType the type of the resulting container
    * @param parsedJson the parsed JSON data
    * @return the new container
    */
  private def fromJsonData(objectType: ContainerType, parsedJson: JsValue): Container = {
    assert(containerCompanions.contains(objectType))
    containerCompanions(objectType).fromJsonData(parsedJson)
  }

  override protected def shutdown(): Unit = {}

  override protected def handle_GetDatabaseIdentifier: String = baseURL

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = containerCompanions.keys.toVector

  override protected def handle_GetLayerType: LayerType = LayerType.CouchDB

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.CouchDB::verifyDatabaseStructure > Object type [$objectsType] is not supported."
    )

    for {
      response <- ws.url(getDatabaseURLFromType(objectsType))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .get()
      _ <- checkResponse(response, 200, "verifyDatabaseStructure")
    } yield {
      true
    }
  }

  private def getDesignDocString(objectsType: ContainerType): String = {
    var designViews = s""""$queryViewName": {"map": "$queryDesignDocMapFunction"}"""
    if (additionalViews.contains(objectsType)) {
      designViews += "," + additionalViews(objectsType).mkString(",")
    }

    s"""{"views": {$designViews}}"""
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.CouchDB::buildDatabaseStructure > Object type [$objectsType] is not supported."
    )

    for {
      buildResponse <- ws.url(getDatabaseURLFromType(objectsType))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .execute("PUT")
      _ <- checkResponse(buildResponse, 201, "buildDatabaseStructure")
      designResponse <- ws.url(s"${getDatabaseURLFromType(objectsType)}/_design/$queryDesignDocName")
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .put(getDesignDocString(objectsType))
      _ <- checkResponse(designResponse, 201, "buildDatabaseStructure")
    } yield {
      true
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.CouchDB::clearDatabaseStructure > Object type [$objectsType] is not supported."
    )

    for {
      response <- ws.url(getDatabaseURLFromType(objectsType))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .delete()
      _ <- checkResponse(response, 200, "clearDatabaseStructure")
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
                "layerType" -> handle_GetLayerType.toString,
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

  /**
    * Retrieves all containers from the database.
    *
    * @param objectsType the database to query
    * @return the retrieved containers
    */
  private def getAllContainers(objectsType: ContainerType): Future[Vector[Container]] = {
    val viewQueryURL = s"${getDatabaseURLFromType(objectsType)}/_design/$queryDesignDocName/_view/$queryViewName"

    for {
      queryResponse <- ws.url(viewQueryURL)
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .get()
      _ <- checkResponse(queryResponse, 200, "getAllContainers")
      containers <- Future {
        (queryResponse.json \ "rows").get.as[JsArray]
          .value
          .map { c => (c \ "value").get }
          .map { c => fromJsonData(objectsType, c) }
      }
    } yield {
      containers.toVector
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.CouchDB::queryDatabase > Object type [$objectsType] is not supported."
    )

    count_GenericQuery += 1

    getAllContainers(objectsType)
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    assert(
      containerCompanions.contains(objectsType),
      s"core3.database.dals.json.CouchDB::queryDatabase > Object type [$objectsType] is not supported."
    )

    count_CustomQuery += 1

    val companion = containerCompanions(objectsType)

    getAllContainers(objectsType).map {
      containers =>
        containers.filter {
          current =>
            companion.matchCustomQuery(customQueryName, queryParams, current)
        }
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    assert(
      containerCompanions.contains(objectType),
      s"core3.database.dals.json.CouchDB::getObject > Object type [$objectType] is not supported."
    )

    count_Get += 1

    for {
      response <- ws.url(getRequestURLFromType(objectType, objectID))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .get()
      _ <- checkResponse(response, 200, "getObject")
    } yield {
      fromJsonData(objectType, response.json)
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    assert(
      containerCompanions.contains(container.objectType),
      s"core3.database.dals.json.CouchDB::createObject > Object type [${container.objectType}] is not supported."
    )

    count_Create += 1

    val jsonData = containerCompanions(container.objectType).toJsonData(container)

    for {
      response <- ws.url(getRequestURLFromType(container.objectType, container.id))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .put(jsonData)
      _ <- checkResponse(response, Seq(200, 201), "createObject")
    } yield {
      true
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    assert(
      containerCompanions.contains(container.objectType),
      s"core3.database.dals.json.CouchDB::updateObject > Object type [${container.objectType}] is not supported."
    )

    count_Update += 1

    val jsonData = containerCompanions(container.objectType).toJsonData(container)

    for {
      objectRevision <- getRevisionID(container.objectType, container.id)
      response <- ws.url(getRequestURLFromType(container.objectType, container.id))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withQueryString("rev" -> objectRevision)
        .put(jsonData)
      _ <- checkResponse(response, 201, "updateObject")
    } yield {
      true
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    assert(
      containerCompanions.contains(objectType),
      s"core3.database.dals.json.CouchDB::deleteObject > Object type [$objectType] is not supported."
    )

    count_Delete += 1

    for {
      objectRevision <- getRevisionID(objectType, objectID)
      response <- ws.url(getRequestURLFromType(objectType, objectID))
        .withAuth(username, password, WSAuthScheme.BASIC)
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withQueryString("rev" -> objectRevision)
        .delete()
      _ <- checkResponse(response, 200, "deleteObject")
    } yield {
      true
    }
  }
}

object CouchDB extends ComponentCompanion {
  def props(
    hostname: String,
    port: Int,
    schema: String,
    dbUser: String,
    dbUserPassword: String,
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    ws: WSClient
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[CouchDB],
    hostname,
    port,
    schema,
    dbUser,
    dbUserPassword,
    containerCompanions,
    ws,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    ws: WSClient,
    config: Config
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[CouchDB],
    containerCompanions,
    ws,
    config,
    ec,
    timeout
  )

  def props(
    containerCompanions: Map[ContainerType, JSONContainerCompanion],
    ws: WSClient
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[CouchDB],
    containerCompanions,
    StaticConfig.get.getConfig("database.couchdb"),
    ws,
    ec,
    timeout
  )

  override def getActionDescriptors: Seq[ActionDescriptor] = {
    Seq(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
