package controllers

import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.storage.SqlStorageService
import services.users.UserMatcher
import services.webauthn.WebAuthnService
import util.Bytes
import util.RequestImplicits.withLoggedInUser
import util.SessionImplicits._

import javax.inject.Inject

class MultiFactorConfigController @Inject() (webAuthn: WebAuthnService,
                                             storage: SqlStorageService,
                                             cc: ControllerComponents)(implicit userMatcher: UserMatcher) extends AbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  def index = Action { implicit request: Request[AnyContent] =>
    withLoggedInUser { user =>
      val keyIds = webAuthn.getKeysForUser(user)
      Ok(views.html.multi_factor_config(keyIds))
    }
  }

  def deleteKey = Action { implicit request: Request[AnyContent] =>
    (request.getQueryString("key"), request.session.getAuthuedUser) match {
      case (Some(keyId), Some(user)) =>
        // we have the key in the query param as url-safe base64, so we need to convert as the database stores
        // everything as "normal" base64
        val fixedKeyId = Bytes.fromUrlBase64(keyId).asBase64
        val rows = storage.deleteCredentialForUsername(user.username, fixedKeyId)
        Logger.info(s"Deleted $rows rows")
        Redirect(routes.MultiFactorConfigController.index())
      case _ => Forbidden(views.html.denied("Must be logged in or specify a valid key id"))
    }
  }

}
