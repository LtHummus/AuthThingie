package config

import java.io.File

import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import services.rules.PathRule
import configs.syntax._
import services.users.User

@Singleton
class TraefikCopConfig @Inject() () {

  private val ConfigTree = ConfigFactory.parseFile(new File(sys.env("CONFIG_FILE_PATH")))

  //these technically could be lazy, but I want checking to happen on startup
  val getPathRules: List[PathRule] = ConfigTree.get[List[PathRule]]("rules").value
  val getUsers: List[User] = ConfigTree.get[List[User]]("users").value
  val getSiteUrl: String = ConfigTree.get[String]("auth_url").value
  val getRealm: String = ConfigTree.get[String]("realm").value

}
