package services.webauthn

import com.github.blemale.scaffeine.Scaffeine
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.anchor.{SimpleTrustAnchorsProvider, TrustAnchorsResolverImpl}
import com.webauthn4j.data.{AuthenticationParameters, AuthenticationRequest, RegistrationParameters, RegistrationRequest}
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.attestation.statement.AttestationStatementValidator
import com.webauthn4j.validator.attestation.statement.packed.PackedAttestationStatementValidator
import com.webauthn4j.validator.attestation.statement.tpm.TPMAttestationStatementValidator
import com.webauthn4j.validator.attestation.trustworthiness.certpath.TrustAnchorCertPathTrustworthinessValidator
import com.webauthn4j.validator.attestation.trustworthiness.self.DefaultSelfAttestationTrustworthinessValidator
import config.AuthThingieConfig
import services.storage.SqlStorageService
import services.users.User
import util.Bytes

import java.util
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava

@Singleton
class WebAuthnService @Inject()(storage: SqlStorageService, config: AuthThingieConfig) {

  private val Logger = play.api.Logger(this.getClass)

  // TODO: this is bad
  private val rp = RelayingParty(config.webauthn.map(_.displayName).orNull, config.webauthn.map(_.rp).orNull)

  private val manager = WebAuthnManager.createNonStrictWebAuthnManager()

  private val RegistrationCacheChallenge = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, RegistrationPayload]()

  private val AuthenticationCache = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, AuthenticationPayload]()

  def generateRegistrationPayload(user: User, residentKey: Boolean): RegistrationInfo = {
    val u = storage.createOrGetUser(user.username)
    val challengeBytes = Bytes.cryptoRandom(32)
    val id = Bytes.cryptoRandom(16)

    val currentKeys = storage.getCredentialIdsForUsername(user.username).map(_.asBase64)

    val payload = RegistrationPayload(u.handle.asBase64, user.username, challengeBytes.asBase64, residentKey, currentKeys.toList, rp)
    RegistrationCacheChallenge.put(id.asUrlBase64, payload)

    RegistrationInfo(id.asUrlBase64, payload)
  }

  def completeRegistration(user: User, info: RegistrationCompletionInfo): Boolean = {
    val u = storage.getUserByUsername(user.username)
    if (u.isEmpty) {
      false
    } else {
      val registrationPayload = RegistrationCacheChallenge.getIfPresent(info.id)
      registrationPayload match {
        case None =>
          Logger.warn(s"could not find challenge info ${info.id}")
          false
        case Some(payload) =>
          val req = new RegistrationRequest(info.attestationObjectBytes, info.clientDataBytes)
          val serverData = new ServerProperty(new Origin("https://" + rp.id), rp.id, new DefaultChallenge(Bytes.fromBase64(payload.challenge).byteArray), null)

          val registrationData = manager.parse(req)
          val validationResult = manager.validate(registrationData, new RegistrationParameters(serverData, false, false))
          Logger.info(s"Validation result = ${validationResult}")

          storage.persistKey(u.get.id, SavedKey.from(validationResult))
          true
      }

    }
  }

  def generateAuthenticationPayload(user: Option[User]): AuthenticationInfo = {
    val allowedKeys = user match {
      case None => List()
      case Some(u) =>
        storage.getCredentialIdsForUsername(u.username).map(_.asBase64).toList
    }

    val challenge = Bytes.cryptoRandom(32)
    val id = Bytes.cryptoRandom(16)

    val payload = AuthenticationPayload(challenge.asBase64, allowedKeys, rp)
    AuthenticationCache.put(id.asUrlBase64, payload)

    AuthenticationInfo(id.asUrlBase64, payload)
  }

  def completeAuthentication(user: Option[User], authenticationCompletionInfo: AuthenticationCompletionInfo): Boolean = {
    val dataNeeded = for {
      info <- AuthenticationCache.getIfPresent(authenticationCompletionInfo.id)
      key  <- storage.getKeyById(authenticationCompletionInfo.keyIdBytes)
    } yield (info, key)

    dataNeeded match {
      case None => false
      case Some((payload, key)) =>
        val serverData = new ServerProperty(new Origin("https://" + rp.id), rp.id, new DefaultChallenge(Bytes.fromBase64(payload.challenge).byteArray), null)
        val authReq = new AuthenticationRequest(authenticationCompletionInfo.keyIdBytes,
          authenticationCompletionInfo.authenticatorDataBytes,
          authenticationCompletionInfo.clientDataBytes,
          authenticationCompletionInfo.signatureBytes)
        val params = new AuthenticationParameters(serverData, key.asAuthenticator, false)

        val authData = manager.parse(authReq)
        val res = manager.validate(authData, params)
        storage.updateSignCounter(res.getCredentialId, res.getAuthenticatorData.getSignCount)

        // TODO: additional checks here
        true
    }

  }
}
