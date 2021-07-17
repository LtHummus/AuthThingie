package controllers

import play.api.libs.json.Json
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.users.UserMatcher
import services.webauthn.WebAuthnService

import javax.inject.{Inject, Singleton}

@Singleton
class AuthnController @Inject() (authn: WebAuthnService, userMatcher: UserMatcher, cc: ControllerComponents) extends AbstractController(cc) {

  def generateRegistrationData = Action { implicit request: Request[AnyContent] =>
    val info = authn.generateRegistrationPayload(userMatcher.getUser("ben").get)
    Ok(Json.toJson(info))
  }
}
