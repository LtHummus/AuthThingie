package controllers

import config.AuthThingieConfig
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}
import services.users.UserMatcher
import util.CallImplicits._

class LoginController @Inject() (config: AuthThingieConfig, userMatcher: UserMatcher, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  val loginForm: Form[LoginData] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )(LoginData.apply)(LoginData.unapply)
  )

  def logout() = Action { implicit request: MessagesRequest[AnyContent] =>
    request.session.get("user") match {
      case Some(user) => Logger.info(s"Logging out $user")
      case None       => Logger.info("Log out called with no user")
    }
    Ok(views.html.logged_out("Logged out successfully", routes.HomeController.index())).withNewSession
  }

  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    val redirectUrl: String = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)
    Unauthorized(views.html.login(loginForm, redirectUrl, routes.LoginController.login().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
  }

  def loginRedirect() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.LoginController.login().url, request.queryString, FOUND)
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)
        BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
    }


    val success = { data: LoginData =>
      val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)

      if (userMatcher.validUser(data.username, data.password).isDefined) {
        Logger.info(s"Successful auth for user ${data.username} from ${request.headers("X-Forwarded-For")}")
        Redirect(redirectUrl, FOUND).withSession(request.session + ("user" -> data.username))
      } else {
        Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers("X-Forwarded-For")}")
        Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get("redirectUrl").getOrElse(""), routes.LoginController.login().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
          .flashing("message" -> "Incorrect username/password")
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }
}
