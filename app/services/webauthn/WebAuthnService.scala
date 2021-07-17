package services.webauthn

import com.github.blemale.scaffeine.Scaffeine
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import config.AuthThingieConfig
import services.storage.SqlStorageService
import services.users.User
import util.Bytes

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt

@Singleton
class WebAuthnService @Inject() (storage: SqlStorageService, config: AuthThingieConfig) {

  private val Logger = play.api.Logger(this.getClass)

  // TODO: this is bad
  private val rp = RelayingParty(config.webauthn.map(_.displayName).orNull, config.webauthn.map(_.rp).orNull)

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
}
