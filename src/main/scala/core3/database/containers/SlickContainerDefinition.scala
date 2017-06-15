package core3.database.containers

import core3.database.ObjectID
import core3.utils.{Date, Time, Timestamp}
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.JdbcProfile

/**
  * Definition trait for containers supporting Slick data handling.
  *
  * <p>
  * <b>Note:</b> When creating the slick table definition, the table name must match the name returned by
  * [[core3.database.containers.BasicContainerDefinition.getDatabaseName]]
  * with all non-alphanumeric characters replaced by underscores (_);
  * for example, the [[core3.database.containers.core.TransactionLog]] container uses 'core-transaction-logs' for the
  * database name and the slick table definition sets the table name as 'core_transaction_logs'.
  * </p>
  */
trait SlickContainerDefinition {
  protected def withProfile: JdbcProfile
  protected lazy val profile: JdbcProfile = withProfile

  import profile.api._

  implicit val columnType_timestamp = MappedColumnType.base[Timestamp, java.sql.Timestamp](
    { jodaTimestamp => new java.sql.Timestamp(jodaTimestamp.getMillis) },
    { javaTimestamp => new Timestamp(javaTimestamp) }
  )

  implicit val columnType_time = MappedColumnType.base[Time, java.sql.Time](
    { jodaTime => new java.sql.Time(jodaTime.toDateTimeToday().getMillis) },
    { javaTime => new Time(javaTime) }
  )

  implicit val columnType_date = MappedColumnType.base[Date, java.sql.Date](
    { jodaDate => new java.sql.Date(jodaDate.toDateTimeAtStartOfDay.getMillis) },
    { javaDate => new Date(javaDate) }
  )

  implicit val columnType_jsValue = MappedColumnType.base[JsValue, String](
    { json => json.toString() },
    { str => Json.parse(str) }
  )

  implicit val columnType_currency = MappedColumnType.base[java.util.Currency, String](
    { currency => currency.toString },
    { str => java.util.Currency.getInstance(str) }
  )

  implicit val columnType_stringVector = MappedColumnType.base[Vector[String], String](
    { seq => seq.mkString(",") },
    { str => if (str.nonEmpty) str.split(",").toVector else Vector.empty }
  )

  implicit val columnType_uuidVector = MappedColumnType.base[Vector[java.util.UUID], String](
    { seq => seq.map(_.toString).mkString(",") },
    { str => if (str.nonEmpty) str.split(",").map(java.util.UUID.fromString).toVector else Vector.empty }
  )

  def createSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema]

  def dropSchemaAction(): DBIOAction[Unit, NoStream, Effect.Schema]

  def genericQueryAction: DBIOAction[Seq[Container], NoStream, Effect.Read]

  def getAction(objectID: ObjectID): DBIOAction[Seq[Container], NoStream, Effect.Read]

  def createAction(container: Container): DBIOAction[Int, NoStream, Effect.Write]

  def updateAction(container: MutableContainer): DBIOAction[Int, NoStream, Effect.Write]

  def deleteAction(objectID: ObjectID): DBIOAction[Int, NoStream, Effect.Write]

  def customQueryAction(queryName: String, queryParams: Map[String, String]): DBIOAction[Seq[Container], NoStream, Effect.Read]
}
