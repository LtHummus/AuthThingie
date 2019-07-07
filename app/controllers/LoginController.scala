package controllers

import config.AuthThingieConfig
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}
import services.users.UserMatcher
import util.CallImplicits._

case class LoginData(username: String, password: String)
case class TotpData(totpCode: String)

class LoginController @Inject() (config: AuthThingieConfig, userMatcher: UserMatcher, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)
  private val PartialAuthExpirationTime = 5 * 60 * 1000 //5 minutes in milliseconds

  val totpForm: Form[TotpData] = Form(
    mapping(
      "totpCode" -> nonEmptyText
    )(TotpData.apply)(TotpData.unapply)
  )

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

  def showTotpForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    val redirectUrl: String = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)
    Ok(views.html.totp(totpForm, routes.LoginController.totp().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
  }

  def totp() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[TotpData] =>
        val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)
        Logger.warn("Unable to parse 2FA form.")
        for (error <- formWithErrors.errors) {
          Logger.warn(s"${error.key} -> ${error.message}")
        }
        BadRequest(views.html.totp(formWithErrors, routes.LoginController.totp().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
    }

    val success = { data: TotpData =>
      val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)

      val knownUser = for {
        potentialUser <- request.session.get("partialAuthUsername")
        user <- userMatcher.getUser(potentialUser)
      } yield {
        user
      }

      knownUser match {
        case Some(user) if user.totpCorrect(data.totpCode) =>
          val givenTimeout = request.session.get("partialAuthTimeout").map(_.toLong).getOrElse(0L)
          if (System.currentTimeMillis() < givenTimeout) {
            Logger.info(s"Successful auth for user ${user.username} from ${request.headers("X-Forwarded-For")}")
            Redirect(redirectUrl, FOUND).withSession("user" -> user.username)
          } else {
            Logger.warn(s"Auth too delayed for user ${user.username} from ${request.headers("X-Forwarded-For")}")
            Unauthorized("TOTP authentication too delayed").withNewSession
          }

        case Some(user) =>
          Logger.warn(s"Bad login attempt for user ${user.username} from ${request.headers("X-Forwarded-For")}")
          Unauthorized(views.html.totp(totpForm, routes.LoginController.totp().appendQueryString(Map("redirect" -> Seq(redirectUrl))))).withSession(request.session)
        case None => Logger.warn("Couldn't find a partially authed username for validation"); BadRequest("Couldn't find a partially authed username for validation")
      }
    }

    totpForm.bindFromRequest.fold(error, success)
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)
        BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
    }


    val success = { data: LoginData =>
      val redirectUrl = request.queryString.get("redirect").flatMap(_.headOption).getOrElse(config.getSiteUrl)

      userMatcher.validUser(data.username, data.password) match {
        case Some(user) if user.doesNotUseTotp =>
          Logger.info(s"Successful auth for user ${user.username} from ${request.headers("X-Forwarded-For")}")
          Redirect(redirectUrl, FOUND).withSession("user" -> user.username)
        case Some(user) if user.usesTotp =>
          Logger.info(s"Successful username/password combo for ${user.username}. Forwarding for 2FA")
          val timeout = System.currentTimeMillis() + PartialAuthExpirationTime
          Redirect(routes.LoginController.totp().appendQueryString(Map("redirect" -> Seq(redirectUrl)))).withSession("partialAuthUsername" -> user.username, "partialAuthTimeout" -> timeout.toString)
        case None =>
          Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers("X-Forwarded-For")}")
          Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get("redirectUrl").getOrElse(""), routes.LoginController.login().appendQueryString(Map("redirect" -> Seq(redirectUrl)))))
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }
}
