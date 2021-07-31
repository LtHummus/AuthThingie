package services.users

import config.AuthThingieConfig
import services.storage.SqlStorageService

import javax.inject.{Inject, Singleton}

@Singleton
class UserMatcher @Inject()(storage: SqlStorageService) {

  def validUser(username: String, password: String): Option[User] = {
    getUser(username) match {
      case Some(user) if user.passwordCorrect(password) => Some(user)
      case _                                            => None
    }
  }
  def getUser(username: String): Option[User] = storage.getUser(username)

}
