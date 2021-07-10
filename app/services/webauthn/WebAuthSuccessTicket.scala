package services.webauthn

import play.api.libs.json.{Json, JsonConfiguration}
import play.api.libs.json.JsonNaming.SnakeCase
import services.hmac.HmacUtils

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import java.util.Base64

case class WebAuthSuccessTicket(username: String, timestamp: Long, redirectUri: String, signature: String) {
  def signaturePayload: String = s"$username\n$timestamp\n$redirectUri\n"
  def timeSinceSignature(timeZone: ZoneId): Duration = {
    val initTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timeZone)
    Duration.between(initTime, ZonedDateTime.now(timeZone))
  }

  def withSignature: WebAuthSuccessTicket = {
    val signature = HmacUtils.sign(signaturePayload)
    this.copy(signature = signature)
  }
  def validateSignature: Boolean = HmacUtils.validate(signaturePayload, signature)

  def validateTicket(timeZone: ZoneId): Boolean = {
    if (!validateSignature) {
      WebAuthSuccessTicket.Logger.warn("Invalid signature")
      false
    } else if (timeSinceSignature(timeZone).compareTo(Duration.ofSeconds(60)) > 0) {
      WebAuthSuccessTicket.Logger.warn("ticket too old")
      false
    } else {
      WebAuthSuccessTicket.Logger.info("ticket ok")
      true
    }
  }
}


object WebAuthSuccessTicket {
  private val Logger = play.api.Logger(this.getClass)

  implicit val config = JsonConfiguration(SnakeCase)
  implicit val format = Json.format[WebAuthSuccessTicket]

  def apply(x: String): WebAuthSuccessTicket = {
    val decoded = Base64.getUrlDecoder.decode(x)
    Json.parse(decoded).as[WebAuthSuccessTicket]
  }
}
