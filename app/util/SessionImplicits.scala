package util

import java.time.{Duration, Instant, ZonedDateTime}

import config.AuthThingieConfig
import play.api.mvc.Session

object SessionImplicits {
  implicit class RichSession(s: Session) {
    def isAuthTimeWithinDuration(d: Duration)(implicit config: AuthThingieConfig): Boolean = {
      s.get("authTime") match {
        case None => false
        case Some(at) =>
          val computedAuthTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at.toLong), config.timeZone)
          ZonedDateTime.now(config.timeZone).minus(d).isBefore(computedAuthTime)
      }
    }

    def getUserAuthedWithin(d: Duration)(implicit config: AuthThingieConfig): Option[String] = {
      (s.get("user"), s.get("authTime")) match {
        case (None, _) => None
        case (_, None) => None
        case (Some(user), Some(authTime)) =>
          val computedAuthTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(authTime.toLong), config.timeZone)
          if (ZonedDateTime.now(config.timeZone).minus(d).isBefore(computedAuthTime)) {
            Some(user)
          } else {
            None
          }
      }
    }
  }
}
