package controllers

import config.AuthThingieConfig
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.ticket.EntryTicketService
import services.users.{User, UserMatcher}
import services.webauthn.{AuthenticationCompletionInfo, RegistrationCompletionInfo, WebAuthnService}
import util.Constants.RedirectString

import javax.inject.{Inject, Singleton}

@Singleton
class AuthnController @Inject() (authn: WebAuthnService, userMatcher: UserMatcher, config: AuthThingieConfig, ticketCache: EntryTicketService, cc: ControllerComponents) extends AbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  def home = Action { implicit request: Request[AnyContent] =>
    val user = for {
      username <- request.session.get("user")
      user     <- userMatcher.getUser(username)
    } yield user

    user match {
      case None => Forbidden("must be logged in")
      case _    => Ok(views.html.webauthn())
    }
  }

  def generateRegistrationData = Action { implicit request: Request[AnyContent] =>
    if (config.webauthn.isEmpty) {
      Forbidden(Json.obj("error" -> "webauthn not enabled"))
    } else {
      val user = for {
        username <- request.session.get("user")
        user <- userMatcher.getUser(username)
      } yield user

      user match {
        case None => Forbidden("must be logged in")
        case Some(u) =>
          val useResidentKey = request.getQueryString("residentKey").contains("true")
          val info = authn.generateRegistrationPayload(u, useResidentKey)
          Ok(Json.toJson(info))
      }
    }
  }

  def completeRegistration = Action(parse.json[RegistrationCompletionInfo]) { implicit request: Request[RegistrationCompletionInfo] =>
    val user = for {
      username <- request.session.get("user")
      user     <- userMatcher.getUser(username)
    } yield user

    val res = authn.completeRegistration(user.get, request.body)
    Ok(Json.obj("success" -> res.isRight, "message" -> res.merge))

  }

  def beginAuthentication = Action { implicit request: Request[AnyContent] =>
    if (config.webauthn.isEmpty) {
      Forbidden(Json.obj("error" -> "webauthn not enabled"))
    } else {
      val user = for {
        username <- request.session.get("user") //TODO: switch to partial later
        user <- userMatcher.getUser(username)
      } yield user

      val info = authn.generateAuthenticationPayload(user)
      Ok(Json.toJson(info))
    }
  }

  def completeAuthentication = Action(parse.json[AuthenticationCompletionInfo]) { implicit request: Request[AuthenticationCompletionInfo] =>
    val user = for {
      username <- request.session.get("user")  //TODO: switch to partial later
      user     <- userMatcher.getUser(username)
    } yield user

    val redirectUrl = request.session.get(RedirectString).getOrElse(config.siteUrl)
    authn.completeAuthentication(user, request.body) match {
      case Left(error) =>
        Logger.warn(s"could not complete authentication: ${error}")
        Forbidden(Json.obj("successful" -> false, "error" -> error))
      case Right(user) =>
        val ticketId = ticketCache.createTicket("ok", user, redirectUrl)
        Ok(Json.obj("ticketId" -> ticketId, "successful" -> true))
    }
  }
}
