package controllers

import config.AuthThingieConfig
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, Request}
import services.DuoSecurity
import services.users.UserMatcher
import util.CallImplicits._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class LoginData(username: String, password: String)
case class TotpData(totpCode: String)

class LoginController @Inject() (config: AuthThingieConfig, userMatcher: UserMatcher, ds: DuoSecurity, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

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
    Redirect(routes.HomeController.index()).withNewSession
  }


  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    Unauthorized(views.html.login(loginForm,
      redirectUrl,
      routes.LoginController
        .login()
        .appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName, None
    )).withSession(request.session - PartialAuthUsername)
  }

  def loginRedirect() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.LoginController.login().url, request.queryString, FOUND)
  }

  def showTotpForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    if (request.session.get(PartialAuthUsername).isEmpty) {
      Unauthorized(views.html.denied("Error: No partially authed username."))
    } else {
      val duoSigRequest = config.duoSecurity.map{ _ =>
        ds.signRequest(request.session.get(PartialAuthUsername).get)
      }
      // TODO: change to only show 2FA if needed
      Ok(views.html.totp(true, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), None, config.duoSecurity.map(_.apiHostname), duoSigRequest, routes.LoginController.duo().appendQueryString(Map(Redirect -> Seq(redirectUrl)))))
    }
  }

  def duo() = Action { implicit request: MessagesRequest[AnyContent] =>
    val signResult = for {
      signature <- Try(request.body.asFormUrlEncoded.get.get("sig_response").head.head)
      validation <- Try(ds.verifyRequest(signature))
    } yield validation

    signResult match {
      case Success(username) =>
        if (request.session.get(PartialAuthUsername).contains(username)) {
          val knownUser = for {
            potentialUser <- request.session.get(PartialAuthUsername)
            user <- userMatcher.getUser(potentialUser)
          } yield {
            user
          }

          knownUser match {
            case Some(user) =>
              Logger.info(s"Successful auth for ${user.username} via Duo")
              Logger.debug(s"Redirecting to $redirectUrl")
              Redirect(redirectUrl, FOUND).withSession("user" -> user.username, "authTime" -> System.currentTimeMillis().toString)
            case None       => Logger.warn("Couldn't find a partially authed username for validation"); BadRequest("Couldn't find a partially authed username for validation")
          }
        } else {
          Unauthorized("Bad")
        }
      case Failure(e) =>
        Logger.warn(s"Error on Duo auth for ${request.session.get(PartialAuthUsername)}", e)
        Unauthorized("Bad failure")
    }
  }


  def totp() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[TotpData] =>
        Logger.warn("Unable to parse 2FA form.")
        for (error <- formWithErrors.errors) {
          Logger.warn(s"${error.key} -> ${error.message}")
        }
        // TODO: same
        BadRequest(views.html.totp(true, formWithErrors, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), Some("Invalid Request"), None, None, routes.LoginController.duo().appendQueryString(Map(Redirect -> Seq(redirectUrl)))))
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
            Logger.debug(s"Redirecting to $redirectUrl")
            Redirect(redirectUrl, FOUND).withSession("user" -> user.username, "authTime" -> System.currentTimeMillis().toString)

          case Some(user) =>
            Logger.warn(s"Bad login attempt for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
            Unauthorized(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), Some("Invalid Auth Code"), None, None, routes.LoginController.duo().appendQueryString(Map(Redirect -> Seq(redirectUrl))))).withSession(request.session)
          case None => Logger.warn("Couldn't find a partially authed username for validation"); BadRequest("Couldn't find a partially authed username for validation")
        }
      }

    }

    totpForm.bindFromRequest.fold(error, success)
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName, Some("Invalid form input")))
    }


    val success = { data: LoginData =>
      userMatcher.validUser(data.username, data.password) match {
        case Some(user) if user.doesNotUseSecondFactor =>
          Logger.info(s"Successful auth for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Logger.debug(s"Redirecting to $redirectUrl")
          Redirect(redirectUrl, FOUND).withSession("user" -> user.username, "authTime" -> System.currentTimeMillis().toString)
        case Some(user) if user.usesSecondFactor =>
          Logger.info(s"Successful username/password combo for ${user.username}. Forwarding for 2FA")
          val timeout = System.currentTimeMillis() + PartialAuthExpirationTime
          Redirect(routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl)))).withSession(PartialAuthUsername -> user.username, PartialAuthTimeout -> timeout.toString)
        case None =>
          Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get(Redirect + "Url").getOrElse(""), routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName, Some("Invalid username or password")))
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }
}
