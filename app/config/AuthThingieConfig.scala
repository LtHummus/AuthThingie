package config

import java.io.File

import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import services.rules.PathRule
import play.api.Configuration
import services.users.User

@Singleton
class AuthThingieConfig @Inject() (baseConfig: Configuration) {

  private val Logger = play.api.Logger(this.getClass)

  private val ConfigTree = {
    val configFile = new File(sys.env("AUTHTHINGIE_CONFIG_FILE_PATH"))
    Logger.info(s"Loading config from ${configFile.getAbsolutePath}")
    ConfigFactory.parseFile(configFile)
  }

  //these technically could be lazy, but I want checking to happen on startup
  val getPathRules: List[PathRule] = baseConfig.get[List[PathRule]]("rules")
  val getUsers: List[User] = baseConfig.get[List[User]]("users")
  val forceRedirectToHttps: Boolean = baseConfig.getOptional[Boolean]("forceRedirectToHttps").contains(true)
  val siteUrl: String = baseConfig.getDeprecated[String]("authSiteUrl", "auth_site_url")

  val isUsingNewConfig: Boolean = false

}
