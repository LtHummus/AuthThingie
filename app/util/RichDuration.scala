package util

import java.time.Duration

import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder


object RichDuration {
  private val CustomFormatter = new PeriodFormatterBuilder()
    .appendDays()
    .appendSuffix(" day ", " days ")
    .appendHours()
    .appendSuffix(" hour ", " hours ")
    .appendMinutes()
    .appendSuffix(" minute ", " minutes ")
    .appendSeconds()
    .appendSuffix(" second ", " seconds ")
    .toFormatter

  implicit class PrettyPrintableDuration(x: Duration) {
    def prettyPrint: String = CustomFormatter.print(new Period(x.toMillis).normalizedStandard())
  }
}
