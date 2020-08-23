package services.duo

import com.duosecurity.duoweb.DuoWeb
import config.AuthThingieConfig
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.RandomStringUtils

@Singleton
class DuoSecurity @Inject() (config: AuthThingieConfig) {

  private lazy val applicationKey = RandomStringUtils.randomAlphanumeric(40)

  val HostUrl = config.duoSecurity.get.apiHostname

  def signRequest(username: String): String = {
    DuoWeb.signRequest(config.duoSecurity.get.integrationKey, config.duoSecurity.get.secretKey, applicationKey, username)
  }

  def verifyRequest(sigResponse: String): String = {
    DuoWeb.verifyResponse(config.duoSecurity.get.integrationKey, config.duoSecurity.get.secretKey, applicationKey, sigResponse)
  }
}
