package controllers

import java.time.Duration

import akka.actor.ActorSystem
import akka.stream.Materializer
import config.AuthThingieConfig
import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, Request, Result, WebSocket}
import services.duo.{DuoAsyncActor, DuoAsyncAuthStatus, DuoWebAuth}
import services.hmac.HmacUtils
import services.users.{User, UserMatcher}
import util.CallImplicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class LoginData(username: String, password: String)
case class TotpData(totpCode: String)

class LoginController @Inject() (config: AuthThingieConfig, userMatcher: UserMatcher, duoWebAuth: DuoWebAuth, cc: MessagesControllerComponents)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer) extends MessagesAbstractController(cc) {

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
          duoWebAuth.preauth(user.username).map{ di =>
            Ok(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), None, Some(di), routes.LoginController.duoPushStatus()))
          }.recover { _ =>
            InternalServerError(views.html.config_errors(List("Duo credential errors")))
          }
        } else {
          Future.successful(Ok(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), None, None, routes.LoginController.duoPushStatus())))
        }

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
        Future.successful(BadRequest(views.html.totp(showTotp = true, formWithErrors, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), Some("Invalid Request"), None, routes.LoginController.duoPushStatus())))
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
              duoWebAuth.preauth(user.username).map { di =>
                Ok(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), None, Some(di), routes.LoginController.duoPushStatus()))
              }.recover { _ =>
                InternalServerError(views.html.config_errors(List("Duo credential errors")))
              }

            } else {
              Future.successful(Unauthorized(views.html.totp(user.usesTotp, totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl))), Some("Invalid Auth Code"), None, routes.LoginController.duoPushStatus())).withSession(request.session))
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
        Future.successful(BadRequest(views.html.login(formWithErrors, redirectUrl, routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName, Some("Invalid form input"))))
    }


    val success = { data: LoginData =>
      userMatcher.validUser(data.username, data.password) match {
        case Some(user) if shouldShowTotpPage(user) =>
          Logger.info(s"Successful username/password combo for ${user.username}. Forwarding for 2FA")
          val timeout = System.currentTimeMillis() + PartialAuthExpirationTime
          Future.successful(Redirect(routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(redirectUrl)))).withSession(PartialAuthUsername -> user.username, PartialAuthTimeout -> timeout.toString))
        case Some(user) => loginAndRedirect(user)
        case None =>
          Logger.warn(s"Bad login attempt for user ${data.username} from ${request.headers.get(XForwardedFor).getOrElse("Unknown")}")
          Future.successful(Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get(Redirect + "Url").getOrElse(""), routes.LoginController.login().appendQueryString(Map(Redirect -> Seq(redirectUrl))), config.siteName, Some("Invalid username or password"))))
      }

    }

    loginForm.bindFromRequest.fold(error, success)
  }

  def sendPush = Action.async{ request =>
    val deviceName = request.queryString("device").head

    for {
      txid <- duoWebAuth.authAsync(request.session(PartialAuthUsername), "push", deviceName)
    } yield {
      Ok(Json.obj("txId" -> txid.txid))
    }
  }

  def duoPushStatus = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      DuoAsyncActor.props(out, duoWebAuth, request.queryString("txId").head, request.queryString("redirectUrl").head, request.session(PartialAuthUsername), config.timeZone)
    }
  }

  def duoRedirect = Action { implicit request =>
    val payload = DuoAsyncAuthStatus(request.queryString("key").head)

    val knownUser = for {
      potentialUser <- request.session.get(PartialAuthUsername)
      user          <- userMatcher.getUser(potentialUser)
    } yield {
      user
    }
    // time to do a bunch of checks
    if (!payload.validateSignature) {
      Unauthorized(views.html.totp(knownUser.exists(_.usesTotp), totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(payload.redirectUrl))), Some("Invalid Duo key signature"), None, routes.LoginController.duoPushStatus())).withSession(request.session)
    } else if (payload.timeSinceSignature(config.timeZone).compareTo(Duration.ofSeconds(60)) > 0) {
      Unauthorized(views.html.totp(knownUser.exists(_.usesTotp), totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(payload.redirectUrl))), Some("Duo Request timed out"), None, routes.LoginController.duoPushStatus())).withSession(request.session)
    } else if (!request.session.get(PartialAuthUsername).contains(payload.username)) {
      Unauthorized(views.html.totp(knownUser.exists(_.usesTotp), totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(payload.redirectUrl))), Some("Duo auth username does not match"), None, routes.LoginController.duoPushStatus())).withSession(request.session)
    } else if (payload.status != "allow") {
      Unauthorized(views.html.totp(knownUser.exists(_.usesTotp), totpForm, routes.LoginController.totp().appendQueryString(Map(Redirect -> Seq(payload.redirectUrl))), Some("Duo Request Denied"), None, routes.LoginController.duoPushStatus())).withSession(request.session)
    } else {
      knownUser match {
        case Some(user) => loginAndRedirect(user, payload.redirectUrl)
        case None       => BadRequest(Json.obj("error" -> "no redirect url"))
      }

    }
  }
}
