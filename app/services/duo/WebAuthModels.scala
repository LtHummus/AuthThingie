package services.duo

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import java.util.Base64

import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Json, JsonConfiguration}
import services.hmac.HmacUtils

case class PingResponse(time: Long)
object PingResponse {
  implicit val format = Json.format[PingResponse]
}

case class Device(capabilities: List[String], device: String, displayName: Option[String], name: String, number: String, `type`: String)
object Device {
  implicit val format = Json.format[Device]
}
case class PreAuthResponse(result: String, statusMsg: String, devices: List[Device])
object PreAuthResponse {
  implicit val config = JsonConfiguration(SnakeCase)
  implicit val format = Json.format[PreAuthResponse]
}

case class SyncAuthResult(result: String, status: String, statusMsg: String)
object SyncAuthResult {
  implicit val config = JsonConfiguration(SnakeCase)
  implicit val format = Json.format[SyncAuthResult]
}

case class AsyncAuthResult(txid: String)
object AsyncAuthResult {
  implicit val format = Json.format[AsyncAuthResult]
}

case class DuoAsyncAuthStatus(status: String, result: String, statusMsg: String, username: String, redirectUrl: String, time: Long, signature: String) {
  def signaturePayload: String = s"$redirectUrl\n$username\n$time\n$status\n$result"
  def timeSinceSignature(timeZone: ZoneId): Duration = {
    val initTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), timeZone)
    Duration.between(initTime, ZonedDateTime.now(timeZone))
  }
  def withSignature: DuoAsyncAuthStatus = {
    val signature = HmacUtils.sign(signaturePayload)
    this.copy(signature = signature)
  }
  def validateSignature: Boolean = HmacUtils.validate(signaturePayload, signature)
}
object DuoAsyncAuthStatus {
  implicit val config = JsonConfiguration(SnakeCase)
  implicit val format = Json.format[DuoAsyncAuthStatus]

  def apply(syncResult: SyncAuthResult, username: String, redirectUrl: String, time: ZonedDateTime): DuoAsyncAuthStatus = {
    DuoAsyncAuthStatus(syncResult.status, syncResult.result, syncResult.statusMsg, username, redirectUrl, time.toInstant.toEpochMilli, "").withSignature
  }

  def apply(x: String): DuoAsyncAuthStatus = {
    val decoded = Base64.getUrlDecoder.decode(x)
    Json.parse(decoded).as[DuoAsyncAuthStatus]
  }
}