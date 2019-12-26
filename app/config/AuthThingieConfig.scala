package config

import java.io.File

import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import services.rules.PathRule
import configs.syntax._
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
  val forceRedirectToHttps: Boolean = ConfigTree.get[Boolean]("forceRedirectToHttps").valueOrElse(false)
  val siteUrl: String = ConfigTree.get[String]("auth_site_url").value
  val isUsingNewConfig: Boolean = ConfigTree.get[String]("play.http.session.jwt.signatureAlgorithm").toOption.isEmpty

}
