package core3.database.containers

import core3.database.ObjectID
import slick.jdbc.MySQLProfile.backend.DatabaseDef
import slick.jdbc.SQLActionBuilder

import scala.concurrent.{ExecutionContext, Future}

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
  def runCreateSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runDropSchema(db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runGenericQuery(query: SQLActionBuilder, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]]

  def runGet(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Container]

  def runCreate(container: Container, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runUpdate(container: MutableContainer, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runDelete(objectID: ObjectID, db: DatabaseDef)(implicit ec: ExecutionContext): Future[Boolean]

  def runCustomQuery(queryName: String, queryParams: Map[String, String], db: DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Container]]
}

/**
  * Implementation trait for containers supporting Slick data handling.
  *
  * Note: Only used by containers.
  */
trait SlickContainerCompanionImpl[ContainerTupleDef] extends SlickContainerCompanion {
  protected def convertToTuple(container: Container): ContainerTupleDef

  protected def convertFromTuple(tuple: ContainerTupleDef): Container
}
