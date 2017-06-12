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
package core3.database.dals.sql.conversions

import core3.utils._
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.MySQLProfile.api._

object ForMySQLProfile {
  implicit val columnType_timestamp = MappedColumnType.base[Timestamp, java.sql.Timestamp](
    { jodaTimestamp => new java.sql.Timestamp(jodaTimestamp.getMillis)},
    { javaTimestamp => new Timestamp(javaTimestamp) }
  )

  implicit val columnType_time = MappedColumnType.base[Time, java.sql.Time](
    { jodaTime => new java.sql.Time(jodaTime.toDateTimeToday().getMillis)},
    { javaTime => new Time(javaTime) }
  )

  implicit val columnType_date = MappedColumnType.base[Date, java.sql.Date](
    { jodaDate => new java.sql.Date(jodaDate.toDateTimeAtStartOfDay.getMillis)},
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
    { str => if(str.nonEmpty) str.split(",").toVector else Vector.empty }
  )

  implicit val columnType_uuidVector = MappedColumnType.base[Vector[java.util.UUID], String](
    { seq => seq.map(_.toString).mkString(",") },
    { str => if(str.nonEmpty) str.split(",").map(java.util.UUID.fromString).toVector else Vector.empty }
  )
}
