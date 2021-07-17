package services.webauthn

import play.api.libs.json.Json

case class RegistrationPayload(userHandle: String, username: String, challenge: String)
object RegistrationPayload {
  implicit val format = Json.format[RegistrationPayload]
}

case class RegistrationInfo(registrationId: String, registrationPayload: RegistrationPayload)
object RegistrationInfo {
  implicit val format = Json.format[RegistrationInfo]
}
