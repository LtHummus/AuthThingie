package controllers

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}
import services.users.{UserDatabase, UserMatcher}
import util.RequestImplicits.withAdminUser

import javax.inject.{Inject, Singleton}

@Singleton
class UserAdminController @Inject()(userDatabase: UserDatabase, cc: MessagesControllerComponents)(implicit userMatcher: UserMatcher) extends MessagesAbstractController(cc) {

  def index() = Action { implicit request =>
    withAdminUser { u =>
      val users = userDatabase.getAllUsers()
      Ok(views.html.user_admin(users))

    }
  }

  def deleteUser() = Action(parse.formUrlEncoded) { implicit request => {
    withAdminUser { u =>
      request.body.get("username").flatMap(_.headOption) match {
        case Some(usernameToDelete) =>
          if (usernameToDelete == u.username) {
            UnprocessableEntity(views.html.denied("You can not delete yourself"))
          } else {
            userDatabase.deleteUser(usernameToDelete)
            Redirect(routes.UserAdminController.index())
          }
        case None =>
          BadRequest(views.html.denied("No username to delete specified"))
      }
    }
  }
  }
}
