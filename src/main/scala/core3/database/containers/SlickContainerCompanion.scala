package core3.database.containers

import core3.database.ObjectID
import slick.dbio.{DBIOAction, NoStream, Effect}

/**
  * Usage trait for containers supporting Slick data handling.
  *
  * <p>
  * <b>Note:</b> When creating the slick table definition, the table name must match the name returned by
  * [[core3.database.containers.BasicContainerCompanion.getDatabaseName]]
  * with all non-alphanumeric characters replaced by underscores (_);
  * for example, the [[core3.database.containers.core.TransactionLog]] container uses 'core-transaction-logs' for the
  * database name and the slick table definition sets the table name as 'core_transaction_logs'.
  * </p>
  */
trait SlickContainerCompanion extends BasicContainerCompanion {
  def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema]

  def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema]

  def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read]

  def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read]

  def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write]

  def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write]

  def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write]

  def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read]
}
