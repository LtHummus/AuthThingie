package controllers

import at.favre.lib.crypto.bcrypt.BCrypt
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, Request}
import services.storage.SqlStorageService

import javax.inject.{Inject, Singleton}

case class SetupData(username: String, password: String, passwordConfirm: String)

@Singleton
class FirstTimeSetupController @Inject() (storage: SqlStorageService,
                                           cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {
  private val DefaultCost = 10

  val setupForm: Form[SetupData] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "passwordConfirm" -> nonEmptyText
    )(SetupData.apply)(SetupData.unapply)
  )
  def index() = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.first_time_setup(setupForm))
  }

  def completeSetup() = Action { implicit request: MessagesRequest[AnyContent] =>
    val errored = {
      formWithErrors: Form[SetupData] =>
        Ok(views.html.first_time_setup(formWithErrors))
    }

    val successful = { data: SetupData =>
      if (data.password != data.passwordConfirm) {
        Ok(views.html.first_time_setup(setupForm.fill(data)))
      } else {
        val passwordHash = BCrypt.withDefaults().hashToString(DefaultCost, data.password.toCharArray)
        storage.createUser(data.username, passwordHash, isAdmin = true)
        Redirect(routes.HomeController.index())
      }
    }

    setupForm.bindFromRequest.fold(errored, successful)
  }
}
