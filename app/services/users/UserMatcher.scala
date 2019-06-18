package services.users

import config.AuthThingieConfig
import javax.inject.{Inject, Singleton}

@Singleton
class UserMatcher @Inject()(config: AuthThingieConfig) {

  private val Users: List[User] = config.getUsers
  private lazy val userMap: Map[String, User] = Users.groupBy(_.username).mapValues(_.head)

  def validUser(username: String, password: String): Option[User] = {
    userMap.get(username) match {
      case Some(user) if user.passwordCorrect(password) => Some(user)
      case _                                            => None
    }
  }

  def getUser(username: String): Option[User] = userMap.get(username)

}
