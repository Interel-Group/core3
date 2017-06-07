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
package core3.workflows

import akka.actor.Props
import akka.pattern.pipe
import core3.core.Component.{ActionDescriptor, ActionResult}
import core3.core.{Component, ComponentCompanion}
import core3.database.containers.{Container, MutableContainer, core}
import core3.database.dals.DatabaseAbstractionLayer
import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import core3.security.UserTokenBase
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Main system component for executing workflows.
  *
  * @param workflowList a list of all available workflows
  * @param db           a DAL for allowing access to data
  * @param storeLogs    determines when transaction logs are to be created
  * @param readOnlyLogsContent determines the additional content storage for read-only workflow logs
  * @param writeLogsContent determines the additional content storage for write workflow logs
  */
class WorkflowEngineComponent(
  private val workflowList: Vector[WorkflowBase],
  private val db: DatabaseAbstractionLayer,
  private val storeLogs: StoreTransactionLogs,
  private val readOnlyLogsContent: TransactionLogContent,
  private val writeLogsContent: TransactionLogContent
)(implicit ec: ExecutionContext) extends Component {

  import WorkflowEngineComponent._

  private val instanceID = java.util.UUID.randomUUID()
  private val workflows: Map[String, WorkflowContainer] = workflowList.map {
    workflow =>
      workflow.name -> WorkflowContainer(workflow, enabled = true)
  }.toMap
  private val auditLogger = Logger("audit")
  private val queryHandlers = DataQueryHandlers(handle_getGroup, db.getObject, handle_getContainerWithRevision, db.queryDatabase, handle_getAllContainers, db.loadView)

  //stats
  private var count_ExecuteAction: Long = 0
  private var count_SetWorkflowState: Long = 0
  private var count_ExecuteWorkflow: Long = 0

  /**
    * Query handler for retrieving 'Group' containers.
    *
    * @param shortName the short name of the group to be retrieved
    * @return Future[ [[core3.database.containers.core.Group]] ] - the requested group
    */
  private def handle_getGroup(shortName: String): Future[core.Group] = {
    db.queryDatabase(objectsType = "Group", customQueryName = "getByShortName", Map("shortName" -> shortName))
      .map {
        containers =>
          if (containers.size == 1) {
            containers.head.asInstanceOf[core.Group]
          } else {
            val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_getGroup > [${containers.size}] containers found for [Group] with short name [$shortName]."
            auditLogger.warn(message)
            throw new RuntimeException(message)
          }
      }
  }

  /**
    * Query handler for retrieving containers and verifying their revisions.
    *
    * @param containerType  the tye of container to retrieve
    * @param objectID       the ID of the object
    * @param revision       the expected revision ID
    * @param revisionNumber the expected revision number
    * @return Future[ [[core3.database.containers.MutableContainer]] ] - the expected container
    */
  private def handle_getContainerWithRevision(containerType: ContainerType, objectID: ObjectID, revision: RevisionID, revisionNumber: RevisionSequenceNumber): Future[MutableContainer] = {
    db.getObject(containerType, objectID).flatMap {
      case container: MutableContainer =>
        if (container.revision == revision && container.revisionNumber == revisionNumber) {
          Future.successful(container)
        } else {
          Future.failed(
            new RuntimeException(s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_getContainerWithRevision > " +
              s"Expected revision [$revision] with number [$revisionNumber] " +
              s"but found revision [${container.revision}] with number [${container.revisionNumber}] " +
              s"for [$containerType] container with ID [$objectID].")
          )
        }
      case _ =>
        Future.failed(
          new RuntimeException(s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_getContainerWithRevision > Containers of type [$containerType] do not support revisions.")
        )
    }
  }

  /**
    * Query handler for retrieving all containers of a specified type.
    *
    * @param containersType the type of containers to retrieve
    * @return Future[ Vector[Container] ] - the requested data
    */
  private def handle_getAllContainers(containersType: ContainerType): Future[Vector[Container]] = {
    db.queryDatabase(containersType)
  }

  /**
    * Creates a new request transaction log with the supplied data.
    *
    * @param workflow  the workflow associated with the transaction
    * @param params    the parameters for the request
    * @param data      the data retrieved by the system
    * @param userID    the ID of the user that initiated the request
    * @param result    the result of the request
    * @param state     the state of the request
    * @param requestID the ID of the associated request
    * @return
    */
  private def storeTransactionLog(
    workflow: WorkflowBase,
    params: JsValue,
    data: JsValue,
    userID: String,
    result: Boolean,
    state: String,
    requestID: RequestID
  ): Future[Unit] = {
    def createLog(): Future[Unit] = {
      val removeParams = (
        workflow.withSensitiveParams
          || (workflow.readOnly && (readOnlyLogsContent == TransactionLogContent.Empty || readOnlyLogsContent == TransactionLogContent.WithDataOnly))
          || (!workflow.readOnly && (writeLogsContent == TransactionLogContent.Empty || writeLogsContent == TransactionLogContent.WithDataOnly))
        )

      val removeData = (
        workflow.withSensitiveData
          || (workflow.readOnly && (readOnlyLogsContent == TransactionLogContent.Empty || readOnlyLogsContent == TransactionLogContent.WithParamsOnly))
          || (!workflow.readOnly && (writeLogsContent == TransactionLogContent.Empty || writeLogsContent == TransactionLogContent.WithParamsOnly))
        )

      val transactionLog = core.TransactionLog(
        workflow.name,
        requestID,
        workflow.readOnly,
        if (removeParams) Json.obj("removed" -> true) else params,
        if (removeData) Json.obj("removed" -> true) else data,
        userID,
        result,
        state
      )

      db.createObject(transactionLog).map {
        _ => auditLogger.info(s"Created transaction log [${transactionLog.id}] for request [$requestID] and workflow [${workflow.name}] with state [$state].")
      }
    }

    storeLogs match {
      case StoreTransactionLogs.Never => Future.successful(())
      case StoreTransactionLogs.OnReadOnly => if (workflow.readOnly) {
        createLog()
      } else {
        Future.successful(())
      }
      case StoreTransactionLogs.OnWriteOnly => if (!workflow.readOnly) {
        createLog()
      } else {
        Future.successful(())
      }
      case StoreTransactionLogs.Always => createLog()
    }
  }

  private def storeTransactionStart(workflow: WorkflowBase, params: JsValue, data: JsValue, user: UserTokenBase, requestID: RequestID): Future[Unit] = {
    storeTransactionLog(workflow, params, data, user.userID, result = false, "STARTED", requestID)
  }

  private def storeTransactionEnd(workflow: WorkflowBase, params: JsValue, data: JsValue, user: UserTokenBase, result: Boolean, requestID: RequestID): Future[Unit] = {
    storeTransactionLog(workflow, params, data, user.userID, result, if (result) "COMPLETED_SUCCESSFUL" else "COMPLETED_FAILED", requestID)
  }

  /**
    * Verifies object revisions and updates the DB when necessary.
    *
    * @param workflow   the workflow being executed
    * @param outputData the actual output data from the workflow's action
    * @param user       the token of the user that initiated the request
    * @return Future[Unit] - nothing
    */
  private def processOutputData(workflow: WorkflowBase, outputData: OutputData, user: UserTokenBase): Future[Unit] = {
    if (!workflow.readOnly) {
      val validateRevisions = Future.sequence(
        (outputData.update ++ outputData.delete).map {
          affectedContainer =>
            db.getObject(affectedContainer.objectType, affectedContainer.id).flatMap {
              case dbContainer: MutableContainer =>
                if (affectedContainer.revision != dbContainer.revision || affectedContainer.revisionNumber != dbContainer.revisionNumber) {
                  Future.failed(
                    new RuntimeException(s"core3.workflows.WorkflowEngineComponent[$instanceID]::processOutputData > " +
                      s"Expected revision [${affectedContainer.revision}] with number [${affectedContainer.revisionNumber}] " +
                      s"but found revision [${dbContainer.revision}] with number [${dbContainer.revision}] " +
                      s"for [${affectedContainer.objectType}] container with ID [${affectedContainer.id}] while executing workflow [${workflow.name}].")
                  )
                } else {
                  Future.successful(())
                }
            }
        }
      )

      for {
        _ <- validateRevisions
        _ <- Future.sequence(outputData.add.map(db.createObject))
        _ <- Future.sequence(
          outputData.delete.map {
            container =>
              db.deleteObject(container.objectType, container.id)
          }
        )
        _ <- Future.sequence(
          outputData.update.map {
            container =>
              container.revision = core3.database.getNewRevisionID
              container.revisionNumber = core3.database.getNewRevisionSequenceNumber(container.revisionNumber)
              container.updated = core3.utils.Time.getCurrentTimestamp
              container.updatedBy = user.userID
              db.updateObject(container)
          }
        )
      } yield {}
    } else {
      val outputDataSize = outputData.add.size + outputData.update.size + outputData.delete.size
      if (outputDataSize > 0) {
        auditLogger.warn(
          s"core3.workflows.WorkflowEngineComponent[$instanceID]::processOutputData > [$outputDataSize] output containers found " +
            s"while executing read-only workflow [${workflow.name}]; no changes were committed."
        )
      }

      Future.successful(())
    }
  }

  override protected def shutdown(): Unit = {}

  override protected def handle_ExecuteAction(action: String, params: Option[Map[String, Option[String]]]): Future[ActionResult] = {
    count_ExecuteAction += 1

    Future.successful(
      action.toLowerCase match {
        case "stats" =>
          ActionResult(
            wasSuccessful = true,
            message = None,
            data = Some(
              Json.obj(
                "instanceID" -> instanceID,
                "counters" -> Json.obj(
                  "executeAction" -> count_ExecuteAction,
                  "setWorkflowState" -> count_SetWorkflowState,
                  "enqueueWorkflow" -> count_ExecuteWorkflow
                ),
                "workflows" -> Json.obj(
                  "enabled" -> workflows.count(_._2.enabled),
                  "disabled" -> workflows.count(!_._2.enabled),
                  "total" -> workflows.size
                )
              )
            )
          )
      }
    )
  }

  private def handle_SetWorkflowState(workflowName: String, enabled: Boolean): Future[Unit] = {
    count_SetWorkflowState += 1

    if (workflows.contains(workflowName)) {
      workflows(workflowName).enabled = enabled
      auditLogger.warn(s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_SetWorkflowState > Workflow [$workflowName] enabled state set to [$enabled].")
      Future.successful(())
    } else {
      Future.failed(new IllegalArgumentException(s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_SetWorkflowState > Workflow [$workflowName] does not exist."))
    }
  }

  private def handle_ExecuteWorkflow(workflowName: String, rawParams: JsValue, user: UserTokenBase, returnOutputData: Boolean): Future[WorkflowResult] = {
    count_ExecuteWorkflow += 1

    val requestID = getNewRequestID

    try {
      if (workflows.contains(workflowName) && workflows(workflowName).enabled) {
        if (user.permissions.contains(workflowName)) {
          val workflow: WorkflowBase = workflows(workflowName).workflow

          (for {
            params <- workflow.parseParameters(rawParams)
            inputData <- workflow.loadData(params, queryHandlers)
            _ <- storeTransactionStart(workflow, params.asJson, inputData.asJson, user, requestID)
            (result, outputData) <- workflow.executeAction(requestID, user, params, inputData).recover {
              case e =>
                e.printStackTrace()
                val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > [$requestID] - Workflow [$workflowName] action failed with message [${e.getMessage}]"
                (WorkflowResult(wasSuccessful = false, requestID, message = Some(message)), OutputData())
            }
            _ <- if (result.wasSuccessful) processOutputData(workflow, outputData, user) else Future.successful(())
            _ <- storeTransactionEnd(workflow, params.asJson, if (result.wasSuccessful) outputData.asJson else Json.obj(), user, result.wasSuccessful, requestID)
          } yield {
            if (returnOutputData) {
              result.withData(outputData)
            } else {
              result
            }
          }).recover {
            case e =>
              e.printStackTrace()
              val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > [$requestID] - Workflow [$workflowName] failed with message [${e.getMessage}]."
              auditLogger.error(message)
              WorkflowResult(wasSuccessful = false, requestID, message = Some(message))
          }
        } else {
          val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > [$requestID] - User [${user.userID}] not allowed to execute workflow [$workflowName]."
          auditLogger.error(message)
          Future.successful(WorkflowResult(wasSuccessful = false, requestID, message = Some(message)))
        }
      } else if (!workflows.contains(workflowName)) {
        val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > [$requestID] - Workflow [$workflowName] does not exist."
        auditLogger.error(message)
        Future.successful(WorkflowResult(wasSuccessful = false, requestID, message = Some(message)))
      } else {
        val message = s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > [$requestID] - Workflow [$workflowName] is disabled."
        auditLogger.error(message)
        Future.successful(WorkflowResult(wasSuccessful = false, requestID, message = Some(message)))
      }
    } catch {
      case NonFatal(e) =>
        auditLogger.error(
          s"core3.workflows.WorkflowEngineComponent[$instanceID]::handle_ExecuteWorkflow > Unhandled exception encountered while " +
            s"processing workflow [$workflowName]: [${e.getMessage}]."
        )
        throw e
    }
  }

  addReceiver {
    case SetWorkflowState(workflowName, enabled) => handle_SetWorkflowState(workflowName, enabled) pipeTo sender
    case ExecuteWorkflow(workflowName, rawParams, user, returnOutputData) => handle_ExecuteWorkflow(workflowName, rawParams, user, returnOutputData) pipeTo sender
  }
}

object WorkflowEngineComponent extends ComponentCompanion {

  /**
    * Enables or disables the specified workflow.
    *
    * @param workflowName the workflow to be updated
    * @param enabled      the new state
    * @return Future[Unit] - nothing
    */
  case class SetWorkflowState(workflowName: String, enabled: Boolean)

  /**
    * Executes the specified workflow with the supplied parameters.
    *
    * @param workflowName     the workflow to be executed
    * @param rawParams        the raw parameters supplied by the requesting service
    * @param user             the user associated with the request
    * @param returnOutputData set to true, if the output data generated by the workflow is to be returned with the result
    * @return Future[ [[core3.workflows.WorkflowResult]] ] - the result of the operation
    */
  case class ExecuteWorkflow(workflowName: String, rawParams: JsValue, user: UserTokenBase, returnOutputData: Boolean = false)

  /**
    * Internal workflow state container.
    *
    * @param workflow the workflow object to be used by the engine
    * @param enabled  the current workflow state (set to true, when enabled)
    */
  private case class WorkflowContainer(workflow: WorkflowBase, var enabled: Boolean)

  def props(
    workflowList: Vector[WorkflowBase],
    db: DatabaseAbstractionLayer,
    storeLogs: StoreTransactionLogs,
    readOnlyLogsContent: TransactionLogContent,
    writeLogsContent: TransactionLogContent
  )(implicit ec: ExecutionContext): Props =
    Props(
      classOf[WorkflowEngineComponent],
      workflowList,
      db,
      storeLogs,
      readOnlyLogsContent,
      writeLogsContent,
      ec)

  override def getActionDescriptors: Vector[ActionDescriptor] = {
    Vector(ActionDescriptor("stats", "Retrieves the latest component stats", arguments = None))
  }
}
