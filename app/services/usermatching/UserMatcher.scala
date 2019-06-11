package services.usermatching

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}

@Singleton
class UserMatcher @Inject() (config: TraefikCopConfig) {

  private val Users: Map[String, String] = config.getUsers

  def validUser(username: String, password: String): Boolean = {
    Users.get(username) match {
      case None       => false
      case Some(pass) => password == pass ///hoooo boy this needs fixing, but this is still proof of concept
    }
  }

}
