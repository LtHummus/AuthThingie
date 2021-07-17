package services.webauthn

import play.api.libs.json.Json

case class RelayingParty(name: String, id: String)
object RelayingParty {
  implicit val format = Json.format[RelayingParty]
}

case class RegistrationPayload(userHandle: String, username: String, challenge: String, residentKey: Boolean, registeredKeys: List[String], rp: RelayingParty)
object RegistrationPayload {
  implicit val format = Json.format[RegistrationPayload]
}

case class RegistrationInfo(registrationId: String, registrationPayload: RegistrationPayload)
object RegistrationInfo {
  implicit val format = Json.format[RegistrationInfo]
}
