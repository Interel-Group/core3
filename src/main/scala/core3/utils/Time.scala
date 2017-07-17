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
package core3.utils

import java.time._
import java.time.format._
import java.time.temporal.ChronoUnit

import play.api.libs.json._

//enables implicit conversions
import scala.language.implicitConversions

sealed trait TimestampFormat

object TimestampFormat {

  case object DefaultTimestamp extends TimestampFormat

  case object ReadableTimestamp extends TimestampFormat

  case object SortableTimestamp extends TimestampFormat

  case object Html5Timestamp extends TimestampFormat

  case object SqlTimestamp extends TimestampFormat

  case object ReadableDate extends TimestampFormat

  case object SortableDate extends TimestampFormat

  case object ReadableTime extends TimestampFormat

  final val DefaultTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
  final val ReadableTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd MMM yyyy)")
  final val SortableTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  final val Html5TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  final val SqlTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  final val ReadableDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
  final val SortableDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  final val ReadableTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

  def getFormatter(format: TimestampFormat): DateTimeFormatter = {
    format match {
      case TimestampFormat.DefaultTimestamp => TimestampFormat.DefaultTimestampFormatter
      case TimestampFormat.ReadableTimestamp => TimestampFormat.ReadableTimestampFormatter
      case TimestampFormat.SortableTimestamp => TimestampFormat.SortableTimestampFormatter
      case TimestampFormat.Html5Timestamp => TimestampFormat.Html5TimestampFormatter
      case TimestampFormat.SqlTimestamp => TimestampFormat.SqlTimestampFormatter
      case TimestampFormat.ReadableDate => TimestampFormat.ReadableDateFormatter
      case TimestampFormat.SortableDate => TimestampFormat.SortableDateFormatter
      case TimestampFormat.ReadableTime => TimestampFormat.ReadableTimeFormatter
    }
  }
}

sealed trait DateFormat

object DateFormat {

  case object DefaultDate extends DateFormat

  case object ReadableDate extends DateFormat

  case object SortableDate extends DateFormat

  case object Html5Date extends DateFormat

  case object SqlDate extends DateFormat

  final val DefaultDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  final val ReadableDateFormatter: DateTimeFormatter = TimestampFormat.ReadableDateFormatter
  final val SortableDateFormatter: DateTimeFormatter = TimestampFormat.SortableDateFormatter
  final val Html5DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  final val SqlDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def getFormatter(format: DateFormat): DateTimeFormatter = {
    format match {
      case DateFormat.DefaultDate => DateFormat.DefaultDateFormatter
      case DateFormat.ReadableDate => DateFormat.ReadableDateFormatter
      case DateFormat.SortableDate => DateFormat.SortableDateFormatter
      case DateFormat.Html5Date => DateFormat.Html5DateFormatter
      case DateFormat.SqlDate => DateFormat.SqlDateFormatter
    }
  }
}

sealed trait TimeFormat

object TimeFormat {

  case object DefaultTime extends TimeFormat

  case object ReadableTime extends TimeFormat

  case object SortableTime extends TimeFormat

  case object Html5Time extends TimeFormat

  case object SqlTime extends TimeFormat

  final val DefaultTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
  final val ReadableTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  final val SortableTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
  final val Html5TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  final val SqlTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  def getFormatter(format: TimeFormat): DateTimeFormatter = {
    format match {
      case TimeFormat.DefaultTime => TimeFormat.DefaultTimeFormatter
      case TimeFormat.ReadableTime => TimeFormat.ReadableTimeFormatter
      case TimeFormat.SortableTime => TimeFormat.SortableTimeFormatter
      case TimeFormat.Html5Time => TimeFormat.Html5TimeFormatter
      case TimeFormat.SqlTime => TimeFormat.SortableTimeFormatter
    }
  }
}

/**
  * Object for date/time related functions.
  */
object Time {

  class ExtendedTimestamp(val self: Timestamp) {
    def toFormattedString(format: TimestampFormat): String = self.format(TimestampFormat.getFormatter(format))

    def toTimeZone(zone: ZoneId, keepFields: Boolean): Timestamp = {
      if (keepFields) {
        self.withZoneSameLocal(zone)
      }
      else {
        self.withZoneSameInstant(zone)
      }
    }

    def toTimeZone(zone: String, keepFields: Boolean = false): Timestamp = toTimeZone(ZoneId.of(zone), keepFields)

    /**
      * Calculates the difference between this and that timestamps.
      *
      * Notes:
      * - A positive integer is returned if this timestamp is after the supplied one.
      * - A negative integer is returned if this timestamp is before the supplied one.
      * - 0 is returned if the timestamps are the same.
      *
      * @param that second timestamp
      * @return 0, if the timestamps are the same or the difference between them (in seconds)
      */
    def diff(that: Timestamp): Int = {
      val absoluteDifference = Duration.between(self, that).getSeconds.abs.toInt

      if (self isAfter that) {
        absoluteDifference
      } else {
        -absoluteDifference
      }
    }
  }

  class ExtendedDate(val self: Date) {
    def toFormattedString(format: DateFormat): String = self.format(DateFormat.getFormatter(format))
  }

  class ExtendedTime(val self: Time) {
    def toFormattedString(format: TimeFormat): String = self.format(TimeFormat.getFormatter(format))

    /**
      * Calculates the difference between this and that time.
      *
      * Notes:
      * - A positive integer is returned if this time is after the supplied one.
      * - A negative integer is returned if this time is before the supplied one.
      * - 0 is returned if the times are the same.
      *
      * @param that second time
      * @return 0, if the times are the same or the difference between them (in seconds)
      */
    def diff(that: Time): Int = {
      val absoluteDifference = Duration.between(self, that).getSeconds.abs.toInt

      if (self isAfter that) {
        absoluteDifference
      } else {
        -absoluteDifference
      }
    }
  }

  class ConvertibleString(val self: String) {
    def toTimestamp(from: TimestampFormat): Timestamp = ZonedDateTime.parse(self, TimestampFormat.getFormatter(from))

    def toDate(from: DateFormat): Date = LocalDate.parse(self, DateFormat.getFormatter(from))

    def toTime(from: TimeFormat): Time = LocalTime.parse(self, TimeFormat.getFormatter(from))
  }

  implicit def timestampToExtended(t: Timestamp): ExtendedTimestamp = new ExtendedTimestamp(t)

  implicit def dateToExtended(d: Date): ExtendedDate = new ExtendedDate(d)

  implicit def timeToExtended(t: Time): ExtendedTime = new ExtendedTime(t)

  implicit def stringToConvertible(s: String): ConvertibleString = new ConvertibleString(s)

  //TODO - Is it needed to reset the JVM time zone to UTC?
  //DateTimeZone.setDefault(DateTimeZone.UTC)
  //java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))

  /**
    * Retrieves the current UTC date & time.
    *
    * @return the current UTC date & time
    */
  def getCurrentTimestamp: Timestamp = {
    ZonedDateTime.now(ZoneOffset.UTC)
  }

  /**
    * Retrieves the current UTC date.
    *
    * @return the current UTC date
    */
  def getCurrentDate: Date = {
    LocalDate.now(ZoneOffset.UTC)
  }

  /**
    * Retrieves the current UTC time.
    *
    * @return the current UTC time
    */
  def getCurrentTime: Time = {
    LocalTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
  }

  implicit val timestampReads: Reads[Timestamp] = Reads {
    json =>
      json.validate[String].map(_.toTimestamp(TimestampFormat.DefaultTimestamp))
  }

  implicit val dateReads: Reads[Date] = Reads {
    json =>
      json.validate[String].map(_.toDate(DateFormat.DefaultDate))
  }

  implicit val timeReads: Reads[Time] = Reads {
    json =>
      json.validate[String].map(_.toTime(TimeFormat.DefaultTime))
  }

  implicit val timestampWrites: Writes[Timestamp] = Writes {
    timestamp =>
      JsString(timestamp.toFormattedString(TimestampFormat.DefaultTimestamp))
  }

  implicit val dateWrites: Writes[Date] = Writes {
    date =>
      JsString(date.toFormattedString(DateFormat.DefaultDate))
  }

  implicit val timeWrites: Writes[Time] = Writes {
    time =>
      JsString(time.toFormattedString(TimeFormat.DefaultTime))
  }
}
