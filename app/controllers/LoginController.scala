package controllers

import config.AuthThingieConfig
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, Request}
import services.users.UserMatcher
import util.CallImplicits._

import scala.concurrent.duration._

case class LoginData(username: String, password: String)
case class TotpData(totpCode: String)

class LoginController @Inject() (config: AuthThingieConfig, userMatcher: UserMatcher, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val PartialAuthUsername = "partialAuthUsername"
  private val Redirect = "redirect"
  private val XForwardedFor = "X-Forwarded-For"
  private val PartialAuthTimeout = "partialAuthTimeout"


  private val Logger = play.api.Logger(this.getClass)
  private val PartialAuthExpirationTime = 5.minutes.toMillis

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

  private def redirectUrl[T](implicit request: Request[T]): String = {
    request.queryString.get(Redirect).flatMap(_.headOption).getOrElse(config.siteUrl)
  }

  def logout() = Action { implicit request: MessagesRequest[AnyContent] =>
    request.session.get("user") match {
      case Some(user) => Logger.info(s"Logging out $user")
      case None       => Logger.info("Log out called with no user")
    }
    Ok(views.html.logged_out("Logged out successfully", routes.HomeController.index())).withNewSession
  }


  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    Unauthorized(views.html.login(loginForm,
      redirectUrl,
      routes.LoginController
        .login()
        .appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName
    )).withSession(request.session - PartialAuthUsername)
  }

  def loginRedirect() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.LoginController.login().url, request.queryString, FOUND)
  }

  def showTotpForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    if (request.session.get(PartialAuthUsername).isEmpty) {
      Unauthorized(views.html.denied("Error: No partially authed username."))
    } else {
      Ok(views.html.totp(totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl)))))
    }
  }


  def totp() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[TotpData] =>
        Logger.warn("Unable to parse 2FA form.")
        for (error <- formWithErrors.errors) {
          Logger.warn(s"${error.key} -> ${error.message}")
        }
        BadRequest(views.html.totp(formWithErrors, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl)))))
    }

    val success = { data: TotpData =>
      val givenTimeout = request.session.get(PartialAuthTimeout).map(_.toLong).getOrElse(0L)

      if (System.currentTimeMillis() > givenTimeout) {
        Unauthorized("TOTP authentication too delayed. Log in again").withNewSession
      } else {
        val knownUser = for {
          potentialUser <- request.session.get(PartialAuthUsername)
          user <- userMatcher.getUser(potentialUser)
        } yield {
          user
        }

        knownUser match {
          case Some(user) if user.totpCorrect(data.totpCode) =>
            Logger.info(s"Successful auth for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
            Redirect(redirectUrl, FOUND).withSession("user" -> user.username)

          case Some(user) =>
            Logger.warn(s"Bad login attempt for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
            Unauthorized(views.html.totp(totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))))).withSession(request.session)
          case None => Logger.warn("Couldn't find a partially authed username for validation"); BadRequest("Couldn't find a partially authed username for validation")
        }
      }

    }

    totpForm.bindFromRequest.fold(error, success)
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName))
    }


    val success = { data: LoginData =>
      userMatcher.validUser(data.username, data.password) match {
        case Some(user) if user.doesNotUseTotp =>
          Logger.info(s"Successful auth for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Redirect(redirectUrl, FOUND).withSession("user" -> user.username)
        case Some(user) if user.usesTotp =>
          Logger.info(s"Successful username/password combo for ${user.username}. Forwarding for 2FA")
          val timeout = System.currentTimeMillis() + PartialAuthExpirationTime
          Redirect(routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl)))).withSession(PartialAuthUsername -> user.username, PartialAuthTimeout -> timeout.toString)
        case None =>
          Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get(Redirect + "Url").getOrElse(""), routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName))
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }
}
