package controllers

import java.time.Duration
import akka.actor.ActorSystem
import akka.stream.Materializer
import config.AuthThingieConfig

import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.duo.{DuoAsyncActor, DuoAsyncAuthStatus, DuoWebAuth}
import services.ticket.EntryTicketService
import services.users.{User, UserMatcher}
import util.CallImplicits._
import util.Constants._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class LoginData(username: String, password: String)
case class TotpData(totpCode: String)

class LoginController @Inject() (config: AuthThingieConfig,
                                 userMatcher: UserMatcher,
                                 duoWebAuth: DuoWebAuth,
                                 ticketService: EntryTicketService,
                                 cc: MessagesControllerComponents)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer) extends MessagesAbstractController(cc) {

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

  private def shouldShowTotpPage(user: User): Boolean = user.usesTotp || (user.duoEnabled && config.duoSecurity.isDefined)

  private def loginAndRedirect(user: User)(implicit request: Request[_]): Future[Result] = {
    Logger.info(s"Successful auth for ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
    Logger.debug(s"Redirecting to $redirectUrl")
    Future.successful(Redirect(redirectUrl, FOUND).withSession("user" -> user.username, "authTime" -> System.currentTimeMillis().toString))
  }

  private def loginAndRedirect(user: User, redirectUrl: String)(implicit request: Request[_]): Result = {
    Logger.info(s"Successful auth for ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
    Logger.debug(s"Redirecting to $redirectUrl")
    Redirect(redirectUrl, FOUND).withSession("user" -> user.username, "authTime" -> System.currentTimeMillis().toString)
  }

  private def redirectUrl[T](implicit request: Request[T]): String = {
    request.queryString.get(RedirectString).flatMap(_.headOption).getOrElse(config.siteUrl)
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
        .appendQueryString(Map(RedirectString -> Seq(redirectUrl))), config.siteName, None
    )).withSession(request.session - PartialAuthUsername)
  }

  def loginRedirect() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.LoginController.login().url, request.queryString, FOUND)
  }

  def showTotpForm() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val partialAuthUser = for {
      potentialUser <- request.session.get(PartialAuthUsername)
      user <- userMatcher.getUser(potentialUser)
    } yield {
      user
    }

    partialAuthUser match {
      case None => Future.successful(Unauthorized(views.html.denied("Error: No partially authed username.")))
      case Some(user) =>
        if (config.duoSecurity.isDefined && user.duoEnabled) {
          renderDuoTotp(user)
        } else {
          Future.successful(Ok(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), None, None, routes.DuoController.duoPushStatus())))
        }

    }
  }

  private def renderDuoTotp(user: User)(implicit request: MessagesRequest[_]) = {
    duoWebAuth.preauth(user.username).map{ di =>
      Ok(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), None, Some(di), routes.DuoController.duoPushStatus()))
    }.recover { error =>
      Logger.warn("Error contacting Duo", error)
      InternalServerError(views.html.config_errors(List("Duo credential errors")))
    }
  }

  def totp() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[TotpData] =>
        Logger.warn("Unable to parse 2FA form.")
        for (error <- formWithErrors.errors) {
          Logger.warn(s"${error.key} -> ${error.message}")
        }
        // we can assume true here since if they submitted the form, they must have totp enabled
        Future.successful(BadRequest(views.html.totp(showTotp = true, formWithErrors, routes.LoginController.totp().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), Some("Invalid Request"), None, routes.DuoController.duoPushStatus())))
    }

    val success = { data: TotpData =>
      val givenTimeout = request.session.get(PartialAuthTimeout).map(_.toLong).getOrElse(0L)

      if (System.currentTimeMillis() > givenTimeout) {
        Future.successful(Unauthorized("TOTP authentication too delayed. Log in again").withNewSession)
      } else {
        val knownUser = for {
          potentialUser <- request.session.get(PartialAuthUsername)
          user          <- userMatcher.getUser(potentialUser)
        } yield {
          user
        }

        knownUser match {
          case Some(user) if user.totpCorrect(data.totpCode) => loginAndRedirect(user)
          case Some(user) =>
            Logger.warn(s"Bad login attempt for user ${user.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
            if (user.duoEnabled) {
              renderDuoTotp(user)
            } else {
              Future.successful(Unauthorized(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), Some("Invalid Auth Code"), None, routes.DuoController.duoPushStatus())).withSession(request.session))
            }
          case None => Logger.warn("Couldn't find a partially authed username for validation"); Future.successful(BadRequest("Couldn't find a partially authed username for validation"))
        }
      }

    }

    totpForm.bindFromRequest.fold(error, success)
  }

  def login() = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        Future.successful(BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), config.siteName, Some("Invalid form input"))))
    }


    val success = { data: LoginData =>
      userMatcher.validUser(data.username, data.password) match {
        case Some(user) if shouldShowTotpPage(user) =>
          Logger.info(s"Successful username/password combo for ${user.username}. Forwarding for 2FA")
          val timeout = System.currentTimeMillis() + PartialAuthExpirationTime
          Future.successful(Redirect(routes.LoginController.totp().appendQueryString(Map(RedirectString -> Seq(redirectUrl)))).withSession(PartialAuthUsername -> user.username, PartialAuthTimeout -> timeout.toString))
        case Some(user) => loginAndRedirect(user)
        case None =>
          Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Future.successful(Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get(RedirectString + "Url").getOrElse(""), routes.LoginController.login().appendQueryString(Map(RedirectString -> Seq(redirectUrl))), config.siteName, Some("Invalid username or password"))))
      }

    }

    loginForm.bindFromRequest.fold(error, success)
  }

}
