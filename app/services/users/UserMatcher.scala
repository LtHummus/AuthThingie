package services.users

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}

@Singleton
class UserMatcher @Inject() (config: TraefikCopConfig) {

  private val Users: List[User] = config.getUsers

  lazy val userMap: Map[String, User] = Users.groupBy(_.username).mapValues(_.head)

  def validUser(username: String, password: String): Boolean = {
    userMap.get(username) match {
      case None       => false
      case Some(user) => password == user.passwordHash ///hoooo boy this needs fixing, but this is still proof of concept
    }
  }

  def getUser(username: String): Option[User] = userMap.get(username)

}
