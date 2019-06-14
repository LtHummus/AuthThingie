package services.users

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import services.validator.HashValidator

@Singleton
class UserMatcher @Inject()(config: TraefikCopConfig, hashValidator: HashValidator) {

  private val Users: List[User] = config.getUsers

  lazy val userMap: Map[String, User] = Users.groupBy(_.username).mapValues(_.head)

  def validUser(username: String, password: String): Boolean = {
    userMap.get(username) match {
      case None       => false
      case Some(user) => hashValidator.validateHash(user.passwordHash, password)
    }
  }

  def getUser(username: String): Option[User] = userMap.get(username)

}
