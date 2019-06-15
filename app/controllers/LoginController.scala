package controllers

import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}
import services.users.UserMatcher

class LoginController @Inject() (userMatcher: UserMatcher, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  val loginForm: Form[LoginData] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "redirectUrl" -> optional(text)
    )(LoginData.apply)(LoginData.unapply)
  )

  def logout() = Action { implicit request: MessagesRequest[AnyContent] =>
    Logger.info("Logging out")
    Ok("Logged out").withNewSession
  }

  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    val redirectUrl: String = request.session.get("redirect").getOrElse("/")
    Unauthorized(views.html.login(loginForm, redirectUrl, routes.LoginController.login()))
      .withSession(request.session + ("redirectUrl" -> redirectUrl))
  }

  def loginRedirect() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.LoginController.login(), FOUND)
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        //we don't have the redirect url potentially, so pull it from the session
        BadRequest(views.html.login(formWithErrors, request.session.get("redirectUrl").getOrElse(""), routes.LoginController.login()))
    }


    val success = { data: LoginData =>

      if (userMatcher.validUser(data.username, data.password).isDefined) {
        Redirect(data.redirectUrl.getOrElse("/"), FOUND).withSession(request.session + ("authenticated" -> "ok") + ("user" -> data.username))
      } else {
        Logger.warn(s"Bad login attempt for user ${data.username} from ${request.remoteAddress}")
        Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get("redirectUrl").getOrElse(""), routes.LoginController.login()))
          .flashing("message" -> "Incorrect username/password")
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }
}
