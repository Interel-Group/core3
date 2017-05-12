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
package core3

import core3.database.containers._
import core3.database.views.ContainerView
import core3.database.{ContainerType, ObjectID, RevisionID, RevisionSequenceNumber}
import play.api.libs.json._

import scala.concurrent.Future

package object workflows {
  type RequestID = java.util.UUID
  val InvalidRequestID: RequestID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")

  sealed trait StoreTransactionLogs

  object StoreTransactionLogs {

    case object Never extends StoreTransactionLogs

    case object OnReadOnly extends StoreTransactionLogs

    case object OnWriteOnly extends StoreTransactionLogs

    case object Always extends StoreTransactionLogs

    def fromString(value: String): StoreTransactionLogs = {
      value.toLowerCase match {
        case "never" => StoreTransactionLogs.Never
        case "onreadonly" => StoreTransactionLogs.OnReadOnly
        case "onwriteonly" => StoreTransactionLogs.OnWriteOnly
        case "always" => StoreTransactionLogs.Always
        case "read" => StoreTransactionLogs.OnReadOnly
        case "write" => StoreTransactionLogs.OnWriteOnly
        case "off" => StoreTransactionLogs.Never
        case "ro" => StoreTransactionLogs.OnReadOnly
        case "wo" => StoreTransactionLogs.OnWriteOnly
        case "rw" => StoreTransactionLogs.Always
      }
    }
  }

  sealed trait TransactionLogContent

  object TransactionLogContent {

    case object Empty extends TransactionLogContent

    case object WithDataOnly extends TransactionLogContent

    case object WithParamsOnly extends TransactionLogContent

    case object WithDataAndParams extends TransactionLogContent

    def fromString(value: String): TransactionLogContent = {
      value.toLowerCase match {
        case "Empty" => TransactionLogContent.Empty
        case "WithDataOnly" => TransactionLogContent.WithDataOnly
        case "WithParamsOnly" => TransactionLogContent.WithParamsOnly
        case "WithDataAndParams" => TransactionLogContent.WithDataAndParams
      }
    }
  }

  /**
    * Generates a new random request ID.
    *
    * @return the new ID
    */
  def getNewRequestID: RequestID = java.util.UUID.randomUUID()

  /**
    * Attempts to convert the supplied string to a request ID.
    *
    * @param value the string to be converted
    * @return the converted ID
    */
  def getRequestIDFromString(value: String): RequestID = java.util.UUID.fromString(value)

  /**
    * Base trait for handling parsed workflow parameters.
    */
  trait WorkflowParameters {
    /**
      * Converts the workflow parameters to a JSON object.
      *
      * @return the converted data
      */
    def asJson: JsValue
  }

  /**
    * An empty [[core3.workflows.WorkflowParameters]] implementation.
    *
    * Can be used when a workflow requires no parameters.
    */
  case class NoWorkflowParameters() extends WorkflowParameters {
    override def asJson: JsValue = Json.obj()
  }

  /**
    * Base trait for handling incoming workflow data.
    */
  trait InputData {
    /**
      * Converts the workflow data to a JSON object.
      *
      * @return the converted data
      */
    def asJson: JsValue
  }

  /**
    * An empty [[core3.workflows.InputData]] implementation.
    *
    * Can be used when a workflow requires no data from the DB.
    */
  case class NoInputData() extends InputData {
    override def asJson: JsValue = Json.obj()
  }

  /**
    * Container for handling outgoing workflow data.
    *
    * @param add    a list of containers to be added
    * @param update a list of containers to be updated
    * @param delete a list of containers to be removed
    */
  case class OutputData(
    add: Vector[Container] = Vector.empty,
    update: Vector[MutableContainer] = Vector.empty,
    delete: Vector[MutableContainer] = Vector.empty
  ) {
    /**
      * Converts the workflow data to a JSON object.
      *
      * @return the converted data
      */
    def asJson: JsValue = Json.obj(
      "add" -> JsArray(add.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full))),
      "update" -> JsArray(update.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full))),
      "delete" -> JsArray(delete.map(c => JSONConverter.toJsonData(c, JsonDataFormat.Full)))
    )
  }

  /**
    * Container for workflow data query handlers.
    *
    * @param getGroup                 a function for retrieving a group by short name
    * @param getContainer             a function for retrieving a container by type & ID
    * @param getContainerWithRevision a function for retrieving a container by type, ID, revision and revision number
    * @param getContainers            a function for querying containers
    * @param getAllContainers         a function for retrieving all containers of a given type
    * @param loadView                 a function for loading views
    */
  case class DataQueryHandlers(
    getGroup: (String) => Future[core3.database.containers.core.Group],
    getContainer: (ContainerType, ObjectID) => Future[Container],
    getContainerWithRevision: (ContainerType, ObjectID, RevisionID, RevisionSequenceNumber) => Future[MutableContainer],
    getContainers: (ContainerType, String, Map[String, String]) => Future[ContainerSet],
    getAllContainers: (ContainerType) => Future[ContainerSet],
    loadView: (ContainerView) => Future[Unit]
  )

}
