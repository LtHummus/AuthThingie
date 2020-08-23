package config

import com.typesafe.config.Config
import play.api.ConfigLoader

case class DuoSecurityConfig(integrationKey: String, secretKey: String, apiHostname: String)
object DuoSecurityConfig {
  implicit val configLoader = new ConfigLoader[DuoSecurityConfig] {
    override def load(config: Config, path: String): DuoSecurityConfig = {
      val integrationKey = config.getString(path + ".integrationKey")
      val secretKey = config.getString(path + ".secretKey")
      val apiHostname = config.getString(path + ".apiHostname")

      DuoSecurityConfig(integrationKey, secretKey, apiHostname)
    }
  }
}