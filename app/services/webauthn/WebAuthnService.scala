package services.webauthn

import com.github.blemale.scaffeine.Scaffeine
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.attestation.statement.AttestationStatementValidator
import com.webauthn4j.validator.attestation.statement.packed.PackedAttestationStatementValidator
import com.webauthn4j.validator.attestation.statement.tpm.TPMAttestationStatementValidator
import com.webauthn4j.validator.attestation.trustworthiness.certpath.TrustAnchorCertPathTrustworthinessValidator
import config.AuthThingieConfig
import services.storage.SqlStorageService
import services.users.User
import util.Bytes

import java.util
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava

@Singleton
class WebAuthnService @Inject() (storage: SqlStorageService, config: AuthThingieConfig) {

  private val Logger = play.api.Logger(this.getClass)

  // TODO: this is bad
  private val rp = RelayingParty(config.webauthn.map(_.displayName).orNull, config.webauthn.map(_.rp).orNull)


  private val attestors = new util.ArrayList[AttestationStatementValidator]()
  attestors.add(new PackedAttestationStatementValidator())
  attestors.add(new TPMAttestationStatementValidator())

  // TODO: figure out how this works
  private val manager = new WebAuthnManager(attestors, null, null, null, null)

  private val RegistrationCacheChallenge = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, RegistrationPayload]()

  def generateRegistrationPayload(user: User, residentKey: Boolean): RegistrationInfo = {
    val challengeBytes = Bytes.cryptoRandom(32)
    val handle = Bytes.cryptoRandom(16)
    val id = Bytes.cryptoRandom(16)

    val currentKeys = storage.getCredentialIdsForUsername(user.username).map(_.asUrlBase64)

    val payload = RegistrationPayload(handle.asBase64, user.username, challengeBytes.asBase64, residentKey, currentKeys.toList, rp)
    RegistrationCacheChallenge.put(id.asUrlBase64, payload)

    RegistrationInfo(id.asUrlBase64, payload)
  }

  def completeRegistration(user: User, info: RegistrationCompletionInfo): Boolean = {
    val registrationPayload = RegistrationCacheChallenge.getIfPresent(info.id)
    registrationPayload match {
      case None =>
        Logger.warn(s"could not find challenge info ${info.id}")
        false
      case Some(payload) =>
        val req = new RegistrationRequest(info.attestationObjectBytes, info.clientDataBytes)
        val serverData = new ServerProperty(new Origin(rp.id), rp.id, new DefaultChallenge(Bytes.fromBase64(payload.challenge).byteArray), null)

        manager.parse(req)
        true
    }
  }
}
