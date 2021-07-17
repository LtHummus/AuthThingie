package services.webauthn

import play.api.libs.json.Json
import util.Bytes

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

case class RegistrationCompletionInfo(id: String, keyId: String, attestationObject: String, clientData: String) {
  def keyIdBytes: Array[Byte] = Bytes.fromBase64(keyId).byteArray
  def attestationObjectBytes: Array[Byte] = Bytes.fromBase64(attestationObject).byteArray
  def clientDataBytes: Array[Byte] = Bytes.fromBase64(clientData).byteArray
}
object RegistrationCompletionInfo {
  implicit val format = Json.format[RegistrationCompletionInfo]
}