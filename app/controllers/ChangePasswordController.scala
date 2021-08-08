package controllers

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc.{AbstractController, ControllerComponents, MessagesAbstractController, MessagesControllerComponents}
import services.users.{UserDatabase, UserMatcher}
import services.validator.HashValidator
import util.RequestImplicits.withLoggedInUser
import util.SessionImplicits._

import javax.inject.{Inject, Singleton}

case class ChangePasswordData(currentPassword: String, newPassword: String, newPasswordConfirm: String)

@Singleton
class ChangePasswordController @Inject()(userDatabase: UserDatabase,
                                         cc: MessagesControllerComponents)(implicit userMatcher: UserMatcher)  extends MessagesAbstractController(cc) {

  val changePasswordForm: Form[ChangePasswordData] = Form(
    mapping(
      "curr_password" -> nonEmptyText,
      "new_password" -> nonEmptyText,
      "new_password_confirm" -> nonEmptyText
    )(ChangePasswordData.apply)(ChangePasswordData.unapply)
  )

  def renderForm = Action { implicit request =>
    withLoggedInUser { u =>
      Ok(views.html.change_password(changePasswordForm))
    }
  }

  def completePasswordChange = Action { implicit request =>
    withLoggedInUser { u =>
      val errored = {
        formWithErrors: Form[ChangePasswordData] =>
          UnprocessableEntity(views.html.change_password(formWithErrors))
      }

      // Ok(views.html.first_time_setup(setupForm.fill(data)))
      val successful = { data: ChangePasswordData =>
        if (!u.passwordCorrect(data.currentPassword)) {
          UnprocessableEntity(views.html.change_password(changePasswordForm.withError("curr_password", "Password is incorrect")))
        } else if (data.newPassword != data.newPasswordConfirm) {
          UnprocessableEntity(views.html.change_password(changePasswordForm
            .withError("new_password", "Passwords do not match")
            .withError("new_password_confirm", "Passwords do not match")))
        } else {
          userDatabase.updatePassword(u, HashValidator.hashPassword(data.newPassword))
          Redirect(routes.HomeController.index())
        }
      }

      changePasswordForm.bindFromRequest().fold(errored, successful)
    }
  }
}
