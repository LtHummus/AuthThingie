package services.webauthn

import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
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

case class SavedKey(keyId: Bytes, attestedCredentialData: Bytes, statement: Bytes, counter: Long)
object SavedKey {

  private val ObjectConverter = new ObjectConverter()
  private val AttestedDataConverter = new AttestedCredentialDataConverter(ObjectConverter)

  def from(rd: RegistrationData): SavedKey = {
    val keyId = Bytes.fromByteArray(rd.getAttestationObject.getAuthenticatorData.getAttestedCredentialData.getCredentialId)
    val attestedData = Bytes.fromByteArray(AttestedDataConverter.convert(rd.getAttestationObject.getAuthenticatorData.getAttestedCredentialData))

    val envelope = new AttestationStatementEnvelope(rd.getAttestationObject.getAttestationStatement)
    val encodedEnvelope = Bytes.fromByteArray(ObjectConverter.getCborConverter.writeValueAsBytes(envelope))

    // TODO: extensions? transports?

    SavedKey(keyId, attestedData, encodedEnvelope, rd.getAttestationObject.getAuthenticatorData.getSignCount)
  }
}