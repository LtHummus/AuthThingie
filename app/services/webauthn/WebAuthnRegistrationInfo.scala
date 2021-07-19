package services.webauthn

import com.webauthn4j.authenticator.{Authenticator, AuthenticatorImpl}
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.statement.AttestationStatement
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

case class AuthenticationPayload(challenge: String, allowedKeys: List[String], rp: RelayingParty)
object AuthenticationPayload {
  implicit val format = Json.format[AuthenticationPayload]
}

case class AuthenticationInfo(authId: String, authenticationPayload: AuthenticationPayload)
object AuthenticationInfo {
  implicit val format = Json.format[AuthenticationInfo]
}

case class AuthenticationCompletionInfo(id: String, keyId: String, authenticatorData: String, clientData: String, signature: String) {
  def keyIdBytes: Array[Byte] = Bytes.fromBase64(keyId).byteArray
  def authenticatorDataBytes: Array[Byte] = Bytes.fromBase64(authenticatorData).byteArray
  def clientDataBytes: Array[Byte] = Bytes.fromBase64(clientData).byteArray
  def signatureBytes: Array[Byte] = Bytes.fromBase64(signature).byteArray
}
object AuthenticationCompletionInfo {
  implicit val format = Json.format[AuthenticationCompletionInfo]
}

case class SavedKey(keyId: Bytes, attestedCredentialData: Bytes, statement: Bytes, counter: Long) {
  def asAuthenticator: Authenticator = {
    val envelope = SavedKey.ObjectConverter.getCborConverter.readValue(statement.byteArray, classOf[AttestationStatementEnvelope])
    new AuthenticatorImpl(
      SavedKey.AttestedDataConverter.convert(attestedCredentialData.byteArray),
      envelope.getAttestationStatement,
      counter
    )
  }
}

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