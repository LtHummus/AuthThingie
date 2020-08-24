package services.duo

import com.duosecurity.duoweb.DuoWeb
import config.AuthThingieConfig
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.RandomStringUtils

@Singleton
class DuoSecurity @Inject() (config: AuthThingieConfig) {

  private lazy val applicationKey = RandomStringUtils.randomAlphanumeric(40)

  def signRequest(username: String): String = {
    config.duoSecurity match {
      case None => throw new IllegalStateException("Trying to sign Duo auth when not configured!")
      case Some(duo) =>
        DuoWeb.signRequest(duo.integrationKey, duo.secretKey, applicationKey, username)
    }
  }

  def verifyRequest(sigResponse: String): String = {
    config.duoSecurity match {
      case None => throw new IllegalArgumentException("Trying to validate Duo auth when not configured!")
      case Some(duo) =>
        DuoWeb.verifyResponse(duo.integrationKey, duo.secretKey, applicationKey, sigResponse)
    }
  }
}
