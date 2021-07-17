package services.webauthn

import com.github.blemale.scaffeine.Scaffeine
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import services.users.User
import util.Bytes

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt

@Singleton
class WebAuthnService @Inject() () {

  private val Logger = play.api.Logger(this.getClass)

  private val RegistrationCacheChallenge = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, RegistrationPayload]()

  def generateRegistrationPayload(user: User): RegistrationInfo = {
    val challengeBytes = Bytes.cryptoRandom(32)
    val handle = Bytes.cryptoRandom(16)
    val id = Bytes.cryptoRandom(16)

    val payload = RegistrationPayload(handle.asUrlBase64, user.username, challengeBytes.asUrlBase64)
    RegistrationCacheChallenge.put(id.asUrlBase64, payload)

    RegistrationInfo(id.asUrlBase64, payload)
  }
}
