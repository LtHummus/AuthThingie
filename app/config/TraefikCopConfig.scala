package config

import java.io.File

import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import services.pathmatching.PathRule
import configs.syntax._
import services.usermatching.User

@Singleton
class TraefikCopConfig @Inject() () {

  private val ConfigTree = ConfigFactory.parseFile(new File(sys.env("CONFIG_FILE_PATH")))

  def getPathRules: List[PathRule] = ConfigTree.get[List[PathRule]]("rules").value
  //def getUsers: Map[String, String] = ConfigTree.get[Map[String, String]]("users").value
  def getUsers: List[User] = ConfigTree.get[List[User]]("users").value

}
