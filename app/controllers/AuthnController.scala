package controllers

import config.AuthThingieConfig
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.users.{User, UserMatcher}
import services.webauthn.{RegistrationCompletionInfo, WebAuthnService}

import javax.inject.{Inject, Singleton}

@Singleton
class AuthnController @Inject() (authn: WebAuthnService, userMatcher: UserMatcher, config: AuthThingieConfig, cc: ControllerComponents) extends AbstractController(cc) {

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
    val user = for {
      username <- request.session.get("user")
      user     <- userMatcher.getUser(username)
    } yield user

    user match {
      case None    => Forbidden("must be logged in")
      case Some(u) =>
        val useResidentKey = request.getQueryString("residentKey").contains("true")
        val info = authn.generateRegistrationPayload(u, useResidentKey)
        Ok(Json.toJson(info))
    }
  }

  def completeRegistration = Action(parse.json) { implicit request: Request[JsValue] =>
    val user = for {
      username <- request.session.get("user")
      user     <- userMatcher.getUser(username)
    } yield user

    request.body.asOpt[RegistrationCompletionInfo] match {
      case None     => UnprocessableEntity(Json.obj("error" -> "could not parse JSON payload"))
      case Some(ri) =>
        Logger.info(s"got registration info = ${ri}")
        authn.completeRegistration(user.get, ri)
        Ok("ok")
    }
  }
}
