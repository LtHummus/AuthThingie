package util

import java.time.Duration

import org.joda.time.Period
import org.joda.time.format.PeriodFormat


object RichDuration {
  implicit class PrettyPrintableDuration(x: Duration) {
    def prettyPrint: String = PeriodFormat.wordBased().print(new Period(x.toMillis))
  }
}
