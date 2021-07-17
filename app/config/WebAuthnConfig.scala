package config

import com.typesafe.config.Config
import play.api.ConfigLoader

case class WebAuthnConfig(rp: String, displayName: String, database: String)
object WebAuthnConfig {
  implicit val configLoader = new ConfigLoader[WebAuthnConfig] {
    override def load(config: Config, path: String): WebAuthnConfig = {
      val rp = config.getString(path + ".rp")
      val displayName = config.getString(path + ".displayName")
      val database = config.getString(path + ".database")

      WebAuthnConfig(rp, displayName, database)
    }
  }
}
