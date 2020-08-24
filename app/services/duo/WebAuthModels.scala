package services.duo

import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Json, JsonConfiguration}

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