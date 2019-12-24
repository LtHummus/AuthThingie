package config

import java.io.File

import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import services.rules.PathRule
import configs.syntax._
import services.users.User

@Singleton
class AuthThingieConfig @Inject()() {

  private val Logger = play.api.Logger(this.getClass)

  private val ConfigTree = {
    val configFile = new File(sys.env("AUTHTHINGIE_CONFIG_FILE_PATH"))
    Logger.info(s"Loading config from ${configFile.getAbsolutePath}")
    ConfigFactory.parseFile(configFile)
  }

  private val PlayConfigTree = ConfigFactory.load()

  //these technically could be lazy, but I want checking to happen on startup
  val getPathRules: List[PathRule] = ConfigTree.get[List[PathRule]]("rules").value
  val getUsers: List[User] = ConfigTree.get[List[User]]("users").value
  val forceRedirectToHttps: Boolean = ConfigTree.get[Boolean]("forceRedirectToHttps").valueOrElse(false)
  val siteUrl: String = ConfigTree.get[String]("auth_site_url").value
  val isUsingNewConfig: Boolean = PlayConfigTree.get[String]("play.application.loader").toOption.contains("modules.AuthThingieLoader")

}
