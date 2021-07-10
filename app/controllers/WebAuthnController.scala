package controllers

import config.AuthThingieConfig
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.duo.DuoAsyncAuthStatus
import services.users.UserMatcher
import services.webauthn.{WebAuthSuccessTicket, WebAuthnService}

import java.time.ZonedDateTime
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class WebAuthnController @Inject() (webauthn: WebAuthnService, userMatcher: UserMatcher, config: AuthThingieConfig, cc: ControllerComponents) extends AbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  def startRegistration() = Action { request: Request[AnyContent] =>
    val resident = request.getQueryString("resident").contains("yes")
    Logger.info(s"Starting registration with resident = ${resident}")
    request.session.get("user") match {
      case None => Forbidden("must be logged in to do this")
      case Some(username) =>
        val j = webauthn.startRegistration(username, resident)
        Ok(j).as(JSON)
    }
  }

  def complete() = Action { request: Request[AnyContent] =>
    val potentialBody = request.body.asText
    val potentialUsername = request.session.get("user")
    (potentialBody, potentialUsername) match {
      case (Some(body), Some(username)) =>
        val success = webauthn.completeRegistration(username, body)
        if (success) {
          Ok(Json.obj("key" -> ""))
        } else {
          InternalServerError("oh no")
        }
      case _ =>
        BadRequest("bad request")
    }
  }

  def authResident() = Action { request: Request[AnyContent] =>
    val (j, k) = webauthn.startResidentAssert()
    Ok(j).as(JSON).withHeaders("X-Assert-Key" -> k)
  }

  private def generateSuccessMessage(username: String, request: Request[_]): JsObject = {
    val ticket = WebAuthSuccessTicket(username, ZonedDateTime.now(config.timeZone).toEpochSecond * 1000, request.session.get("redirect").getOrElse(config.siteUrl), "").withSignature
    val encodedTicket = Base64.getUrlEncoder.encodeToString(Json.toJson(ticket).toString().getBytes)
    Json.obj("successful" -> true, "ticket" -> encodedTicket)
  }

  def completeAuthResident() = Action { request: Request[AnyContent] =>
    request.getQueryString("key") match {
      case None => BadRequest("no request key included")
      case Some(key) =>
        val success = webauthn.finishResidentAssert(request.body.asText.get, key)
        success match {
          case Some(username) => Ok(generateSuccessMessage(username, request))
          case None           => Forbidden("invalid")
        }
    }
  }

  def authRequest() = Action { request: Request[AnyContent] =>
    request.session.get("partialAuthUsername") match {
      case None => Forbidden("you must be logged in for that")
      case Some(username) =>
        val j = webauthn.startAssertion(username)
        Ok(j).as(JSON)
    }
  }

  def finishAuth() = Action { request: Request[AnyContent] =>
    request.session.get("partialAuthUsername") match {
      case None => Forbidden("you must be logged in for this")
      case Some(username) =>
        val success = webauthn.finishAssertion(username, request.body.asText.get)
        if (success) {
          Ok(generateSuccessMessage(username, request))
        } else {
          Forbidden("invalid")
        }
    }
  }

  def registrationPage() = Action { request: Request[AnyContent] =>
    request.session.get("user") match {
      case None => Forbidden("need to be logged in")
      case Some(username) =>
        val keys = webauthn.listEnrolledKeys(username).map(_.getId)
        Ok(views.html.webauthn(keys))
    }

  }


  def completeRedirect() = Action { request: Request[AnyContent] =>
    request.getQueryString("ticket") match {
      case None => Forbidden("no auth ticket found")
      case Some(ticket) =>
        val t = WebAuthSuccessTicket(ticket)

        if (!t.validateTicket(config.timeZone)) {
          Forbidden("invalid ticket")
        } else {
          Redirect(t.redirectUri, FOUND).withSession("user" -> t.username, "authTime" -> System.currentTimeMillis().toString)
        }
    }

  }

  def unenrollEverything() = Action { request: Request[AnyContent] =>
    request.session.get("user") match {
      case None => Forbidden("you must be logged in for that")
      case Some(username) =>
        webauthn.unenrollUser(username)
        NoContent
    }
  }
}
