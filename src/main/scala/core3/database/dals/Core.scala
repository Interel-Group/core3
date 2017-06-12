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
package core3.database.dals

import akka.actor.{ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import core3.core.Component.{ActionDescriptor, ActionResult, ExecuteAction}
import core3.core.ComponentCompanion
import core3.database.containers._
import core3.database.views.ContainerView
import core3.database.{ContainerType, ObjectID}
import play.api.Logger
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * A Database Abstraction Layer for consolidating database access.
  *
  * Notes:
  * - All DALs need to be initialized/configured before they are passed to the Core
  * - Each read operation always retrieves data from the first DAL in the list.
  * - Each write operation sends the data to all DALs defined for that ContainerType.
  *
  * @constructor creates a new database Core DAL
  * @param DALs DAL input map (a vector of DALs for each ContainerType)
  */
class Core(DALs: Map[ContainerType, Vector[ActorRef]])(implicit ec: ExecutionContext, timeout: Timeout)
  extends DatabaseAbstractionLayerComponent {

  import Core._
  import DatabaseAbstractionLayerComponent._

  private val configuredDALs = DALs
  private val auditLogger = Logger("audit")

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_GenericQuery: Long = 0
  private var count_CustomQuery: Long = 0
  private var count_Get: Long = 0
  private var count_Create: Long = 0
  private var count_Update: Long = 0
  private var count_Delete: Long = 0

  private def logOperation(future: Future[Boolean], caller: String): Unit = {
    future.onComplete {
      case Success(result) =>
        if (result) {
          auditLogger.info(s"core3.database.dals.Core::$caller > Database operation completed successfully.")
        } else {
          auditLogger.error(s"core3.database.dals.Core::$caller > Database operation failed.")
        }
      case Failure(e) =>
        auditLogger.error(s"core3.database.dals.Core::$caller > Database operation failed with exception [${e.getMessage}].")
    }
  }

  override protected def shutdown(): Unit = {}

  override protected def handle_GetDatabaseIdentifier: String = "core3.database.dals.Core"

  override protected def handle_GetSupportedContainers: Vector[ContainerType] = configuredDALs.keys.toVector

  override protected def handle_GetLayerType: LayerType = LayerType.Core

  /**
    * Verifies all configured databases/DALs.
    *
    * @return true, if all verification processes were successful
    */
  private def handle_VerifyAllDatabases(): Future[Boolean] = {
    val result = Future.sequence(
      configuredDALs.keys.map {
        k =>
          (self ? VerifyDatabaseStructure(k)).mapTo[Boolean].recover {
            case e =>
              auditLogger.error(s"core3.database.dals.Core::handle_VerifyAllDatabases > " +
                s"Exception [${e.getMessage}] encountered while verifying databases for [$k] containers.", e)
              false
          }
      }
    ).map(_.forall(c => c))

    logOperation(result, "handle_VerifyAllDatabases")

    result
  }

  /**
    * Builds all configured databases.
    *
    * @return true, if all build processes were successful
    */
  private def handle_BuildAllDatabases(): Future[Boolean] = {
    val result = Future.sequence(
      configuredDALs.keys.map {
        k =>
          (self ? BuildDatabaseStructure(k)).mapTo[Boolean].recover {
            case e =>
              auditLogger.error(s"core3.database.dals.Core::handle_BuildAllDatabases > " +
                s"Exception [${e.getMessage}] encountered while building databases for [$k] containers.", e)
              false
          }
      }
    ).map(_.forall(c => c))

    logOperation(result, "handle_BuildAllDatabases")

    result
  }

  /**
    * Clears all configured databases.
    *
    * @param ignoreErrors denotes whether the operation should stop if an issue is encountered
    * @return true, if all processes were successful
    */
  private def handle_ClearAllDatabases(ignoreErrors: Boolean): Future[Boolean] = {
    val result = Future.sequence(
      configuredDALs.keys.map {
        k =>
          (self ? ClearDatabaseStructure(k))
            .mapTo[Boolean]
            .map {
              result =>
                result || ignoreErrors
            }
            .recover {
              case e =>
                auditLogger.error(s"core3.database.dals.Core::handle_ClearAllDatabases > " +
                  s"Exception [${e.getMessage}] encountered while clearing databases for [$k] containers.", e)
                ignoreErrors && NonFatal(e)
            }
      }
    ).map(_.forall(c => c))

    logOperation(result, "handle_ClearAllDatabases")

    result
  }

  /**
    * Synchronizes all configured databases/DALs.
    *
    * @return true, if all verification processes were successful
    */
  private def handle_SynchronizeAllDatabases(): Future[Boolean] = {
    val result = Future.sequence(
      configuredDALs.keys.map {
        k =>
          (self ? SynchronizeDatabases(Some(k))).mapTo[Boolean].recover {
            case e =>
              auditLogger.error(s"core3.database.dals.Core::handle_SynchronizeAllDatabases > " +
                s"Exception [${e.getMessage}] encountered while synchronizing databases for [$k] containers.", e)
              false
          }
      }
    ).map(_.forall(c => c))

    logOperation(result, "handle_SynchronizeAllDatabases")

    result
  }

  override protected def handle_VerifyDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (!configuredDALs.contains(objectsType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_VerifyDatabaseStructure > No DALs found for container type [$objectsType].")
      )
    } else {
      val result = Future.sequence(
        configuredDALs(objectsType).map {
          currentDAL =>
            (currentDAL ? VerifyDatabaseStructure(objectsType)).mapTo[Boolean].recover {
              case e =>
                auditLogger.error(s"core3.database.dals.Core::handle_VerifyDatabaseStructure > " +
                  s"Exception [${e.getMessage}] encountered while verifying databases for [$objectsType] containers.", e)
                false
            }
        }
      ).map(_.forall(c => c))

      logOperation(result, s"handle_VerifyDatabaseStructure($objectsType)")

      result
    }
  }

  override protected def handle_BuildDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (!configuredDALs.contains(objectsType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_BuildDatabaseStructure > No DALs found for container type [$objectsType].")
      )
    } else {
      val result = Future.sequence(
        configuredDALs(objectsType).map {
          currentDAL =>
            (currentDAL ? BuildDatabaseStructure(objectsType)).mapTo[Boolean].recover {
              case e =>
                auditLogger.error(s"core3.database.dals.Core::handle_BuildDatabaseStructure > " +
                  s"Exception [${e.getMessage}] encountered while building databases for [$objectsType] containers.", e)
                false
            }
        }
      ).map(_.forall(c => c))

      logOperation(result, s"handle_BuildDatabaseStructure($objectsType)")

      result
    }
  }

  override protected def handle_ClearDatabaseStructure(objectsType: ContainerType): Future[Boolean] = {
    if (!configuredDALs.contains(objectsType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_ClearDatabaseStructure > No DALs found for container type [$objectsType].")
      )
    } else {
      val result = Future.sequence(
        configuredDALs(objectsType).map {
          currentDAL =>
            (currentDAL ? ClearDatabaseStructure(objectsType)).mapTo[Boolean].recover {
              case e =>
                auditLogger.error(s"core3.database.dals.Core::handle_ClearDatabaseStructure > " +
                  s"Exception [${e.getMessage}] encountered while clearing databases for [$objectsType] containers.", e)
                false
            }
        }
      ).map(_.forall(c => c))

      logOperation(result, s"handle_ClearDatabaseStructure($objectsType)")

      result
    }
  }

  /**
    * Synchronizes all databases of the specified type.
    *
    * Notes:
    * - Synchronization is done from the master DB (first in the list for each container type) to all salve DBs.
    * - Each slave DB is cleared, rebuilt and all objects from the master are recreated.
    *
    * @param objectsType the type of objects to synchronize
    * @return true, if all processes were successful
    * @throws RuntimeException if an error occurs during synchronization
    */
  private def handle_SynchronizeDatabases(objectsType: ContainerType): Future[Boolean] = {
    val master = configuredDALs(objectsType).head
    val slaves = configuredDALs(objectsType).drop(1)

    val result = Future.sequence(
      slaves.map {
        currentSlaveDAL =>
          (for {
            _ <- (currentSlaveDAL ? ClearDatabaseStructure(objectsType)).recover { case NonFatal(e) => e.printStackTrace(); true }
            _ <- currentSlaveDAL ? BuildDatabaseStructure(objectsType)
            masterContainers <- (master ? GetGenericQueryResult(objectsType)).mapTo[Vector[Container]]
            syncResult <- Future.sequence(masterContainers.map(c => (currentSlaveDAL ? CreateObject(c)).mapTo[Boolean])).map(_.forall(c => c))
          } yield {
            syncResult
          }).recover {
            case e =>
              auditLogger.error(s"core3.database.dals.Core::handle_SynchronizeDatabases > " +
                s"Exception [${e.getMessage}] encountered while synchronizing databases for [$objectsType] containers.", e)
              false
          }
      }
    ).map(_.forall(c => c))

    logOperation(result, s"handle_SynchronizeDatabases($objectsType)")

    result
  }

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    action.toLowerCase match {
      case "stats" =>
        for {
          results <- Future.sequence(
            configuredDALs.values.flatten.toSeq.distinct.map {
              current =>
                (current ? ExecuteAction("stats", params = None)).mapTo[ActionResult]
            }
          )
        } yield {
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "layerType" -> handle_GetLayerType,
                "supportedContainers" -> handle_GetSupportedContainers,
                "id" -> handle_GetDatabaseIdentifier,
                "dals" -> Json.obj(
                  "config" -> configuredDALs.map {
                    case (k, v) => k -> v.map(c => c.toString)
                  },
                  "stats" -> results.map(result => result.data.orElse(result.message.map(JsString)).getOrElse(JsBoolean(result.wasSuccessful)))
                ),
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

      case "sync" =>
        params match {
          case Some(actualParams) =>
            actualParams.get("objectsType").flatten match {
              case Some("all") => handle_SynchronizeAllDatabases().map(result => ActionResult(wasSuccessful = result))
              case Some(objectsType) => handle_SynchronizeDatabases(objectsType).map(result => ActionResult(wasSuccessful = result))
              case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Parameter [objectsType] is required")))
            }

          case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"No parameters supplied")))
        }

      case "build" =>
        params match {
          case Some(actualParams) =>
            actualParams.get("objectsType").flatten match {
              case Some("all") => handle_BuildAllDatabases().map(result => ActionResult(wasSuccessful = result))
              case Some(objectsType) => handle_BuildDatabaseStructure(objectsType).map(result => ActionResult(wasSuccessful = result))
              case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Parameter [objectsType] is required")))
            }

          case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"No parameters supplied")))
        }

      case "verify" =>
        params match {
          case Some(actualParams) =>
            actualParams.get("objectsType").flatten match {
              case Some("all") => handle_VerifyAllDatabases().map(result => ActionResult(wasSuccessful = result))
              case Some(objectsType) => handle_VerifyDatabaseStructure(objectsType).map(result => ActionResult(wasSuccessful = result))
              case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Parameter [objectsType] is required")))
            }

          case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"No parameters supplied")))
        }

      case "clear" =>
        params match {
          case Some(actualParams) =>
            actualParams.get("objectsType").flatten match {
              case Some("all") => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Set parameter [objectsType] to [ALL] to clear all databases")))
              case Some("ALL") => handle_ClearAllDatabases(ignoreErrors = false).map(result => ActionResult(wasSuccessful = result))
              case Some(objectsType) => handle_ClearDatabaseStructure(objectsType).map(result => ActionResult(wasSuccessful = result))
              case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"Parameter [objectsType] is required")))
            }

          case None => Future.successful(ActionResult(wasSuccessful = false, message = Some(s"No parameters supplied")))
        }
    }
  }

  override protected def handle_GetGenericQueryResult(objectsType: ContainerType): Future[Vector[Container]] = {
    if (!configuredDALs.contains(objectsType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_GetGenericQueryResult > No DALs found for container type [$objectsType].")
      )
    } else {
      count_GenericQuery += 1

      val result = (configuredDALs(objectsType).head ? GetGenericQueryResult(objectsType)).mapTo[Vector[Container]]

      result.onComplete {
        case Success(r) =>
          auditLogger.info(s"core3.database.dals.Core::handle_GetGenericQueryResult($objectsType) > Database query completed successfully and found [${r.size}] container(s).")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_GetGenericQueryResult($objectsType) > Database query failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_GetCustomQueryResult(objectsType: ContainerType, customQueryName: String, queryParams: Map[String, String]): Future[Vector[Container]] = {
    if (!configuredDALs.contains(objectsType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_GetCustomQueryResult > No DALs found for container type [$objectsType].")
      )
    } else {
      count_CustomQuery += 1

      val result = (configuredDALs(objectsType).head ? GetCustomQueryResult(objectsType, customQueryName, queryParams)).mapTo[Vector[Container]]

      result.onComplete {
        case Success(r) =>
          auditLogger.info(s"core3.database.dals.Core::handle_GetCustomQueryResult($objectsType) > Database query [$customQueryName] completed successfully and found [${r.size}] container(s).")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_GetCustomQueryResult($objectsType) > Database query [$customQueryName] failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_LoadView(view: ContainerView): Future[Unit] = {
    if (!configuredDALs.contains(view.coreObjectType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_LoadView > No DALs found for container type [${view.coreObjectType}].")
      )
    } else {
      val result = (configuredDALs(view.coreObjectType).head ? LoadView(view)).mapTo[Unit]

      result.onComplete {
        case Success(_) =>
          auditLogger.info(s"core3.database.dals.Core::handle_LoadView > Database view load [${view.viewType}] for core object [${view.coreObjectType} / ${view.coreObjectID}]  completed successfully.")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_LoadView > Database view load [${view.viewType}] for core object [${view.coreObjectType} / ${view.coreObjectID}] failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_GetObject(objectType: ContainerType, objectID: ObjectID): Future[Container] = {
    if (!configuredDALs.contains(objectType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_GetObject > No DALs found for container type [$objectType].")
      )
    } else {
      count_Get += 1

      val result = (configuredDALs(objectType).head ? GetObject(objectType, objectID)).mapTo[Container]

      result.onComplete {
        case Success(_) =>
          auditLogger.info(s"core3.database.dals.Core::handle_GetObject($objectType) > Database query for object [$objectID] completed successfully.")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_GetObject($objectType) > Database query for object [$objectID] failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_CreateObject(container: Container): Future[Boolean] = {
    if (!configuredDALs.contains(container.objectType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_CreateObject > No DALs found for container type [${container.objectType}].")
      )
    } else {
      count_Create += 1

      val result = Future.sequence(
        configuredDALs(container.objectType).map {
          currentDAL =>
            (currentDAL ? CreateObject(container)).mapTo[Boolean].recoverWith {
              case e =>
                for {
                  dbIdentifier <- (currentDAL ? GetDatabaseIdentifier()).mapTo[String]
                } yield {
                  auditLogger.error(s"core3.database.dals.Core::handle_CreateObject(${container.objectType}) > "
                    + s"Exception [${e.getMessage}] encountered while creating object [${container.id}] in database [$dbIdentifier].", e)
                  false
                }
            }
        }
      ).map(_.forall(c => c))

      result.onComplete {
        case Success(_) =>
          auditLogger.info(s"core3.database.dals.Core::handle_CreateObject(${container.objectType}) > Object [${container.id}] creation completed successfully.")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_CreateObject(${container.objectType}) > Object [${container.id}] creation failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_UpdateObject(container: MutableContainer): Future[Boolean] = {
    if (!configuredDALs.contains(container.objectType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_UpdateObject > No DALs found for container type [${container.objectType}].")
      )
    } else {
      count_Update += 1

      val result = Future.sequence(
        configuredDALs(container.objectType).map {
          currentDAL =>
            (currentDAL ? UpdateObject(container)).mapTo[Boolean].recoverWith {
              case e =>
                for {
                  dbIdentifier <- (currentDAL ? GetDatabaseIdentifier()).mapTo[String]
                } yield {
                  auditLogger.error(s"core3.database.dals.Core::handle_UpdateObject(${container.objectType}) > " +
                    s"Exception [${e.getMessage}] encountered while updating object [${container.id}] in database [$dbIdentifier].", e)
                  false
                }
            }
        }
      ).map(_.forall(c => c))

      result.onComplete {
        case Success(_) =>
          auditLogger.info(s"core3.database.dals.Core::handle_UpdateObject(${container.objectType}) > Object [${container.id}] update completed successfully.")
        case Failure(e) =>
          auditLogger.error(s"core3.database.dals.Core::handle_UpdateObject(${container.objectType}) > Object [${container.id}] update failed with exception [${e.getMessage}].")
      }

      result
    }
  }

  override protected def handle_DeleteObject(objectType: ContainerType, objectID: ObjectID): Future[Boolean] = {
    if (!configuredDALs.contains(objectType)) {
      Future.failed(
        new IllegalArgumentException(s"core3.database.dals.Core::handle_DeleteObject > No DALs found for container type [$objectType].")
      )
    } else {
      count_Delete += 1

      Future.sequence(
        configuredDALs(objectType).map {
          currentDAL =>
            (currentDAL ? DeleteObject(objectType, objectID)).mapTo[Boolean].recoverWith {
              case e =>
                for {
                  dbIdentifier <- (currentDAL ? GetDatabaseIdentifier()).mapTo[String]
                } yield {
                  auditLogger.error(s"core3.database.dals.Core::handle_DeleteObject($objectType) > " +
                    s"Exception [${e.getMessage}] encountered while deleting object [$objectID] in database [$dbIdentifier].", e)
                  false
                }
            }
        }
      ).map(_.forall(c => c))
    }
  }

  addReceiver {
    case BuildAllDatabases() => handle_BuildAllDatabases() pipeTo sender

    case ClearAllDatabases(ignoreErrors) => handle_ClearAllDatabases(ignoreErrors) pipeTo sender

    case VerifyAllDatabases() => handle_VerifyAllDatabases() pipeTo sender

    case SynchronizeDatabases(objectsTypeOpt) =>
      objectsTypeOpt match {
        case Some(objectsType) => handle_SynchronizeDatabases(objectsType) pipeTo sender
        case None => handle_SynchronizeAllDatabases() pipeTo sender
      }
  }
}

object Core extends ComponentCompanion {

  /**
    * Builds all configured databases.
    *
    * @return Future[Boolean] - true, if all build processes were successful
    */
  case class BuildAllDatabases()

  /**
    * Clears all configured databases.
    *
    * @param ignoreErrors denotes whether the operation should stop if an issue is encountered
    * @return Future[Boolean] - true, if all processes were successful
    */
  case class ClearAllDatabases(ignoreErrors: Boolean = false)

  /**
    * Verifies all configured databases/DALs.
    *
    * @return Future[Boolean] - true, if all verification processes were successful
    */
  case class VerifyAllDatabases()

  /**
    * Synchronizes all configured databases/DALs.
    *
    * @param objectsType the type of databases to synchronize (or None to sync all types)
    * @return Future[Boolean] - true, if all verification processes were successful
    */
  case class SynchronizeDatabases(objectsType: Option[ContainerType] = None)

  def props(
    DALs: Map[ContainerType, Vector[ActorRef]]
  )(implicit ec: ExecutionContext, timeout: Timeout): Props = Props(
    classOf[Core],
    DALs,
    ec,
    timeout
  )

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(
      ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None),
      ActionDescriptor("sync", "Synchronizes data between DALs", arguments = Some(Map("objectsType" -> "(required) [all|<container type>]"))),
      ActionDescriptor("build", "Builds one or more of the configured databases", arguments = Some(Map("objectsType" -> "(required) [all|<container type>]"))),
      ActionDescriptor("verify", "Verifies one or more of the configured databases", arguments = Some(Map("objectsType" -> "(required) [all|<container type>]"))),
      ActionDescriptor("clear", "Clears one or more of the configured databases", arguments = Some(Map("objectsType" -> "(required) [ALL|<container type>]")))
    )
  }
}
