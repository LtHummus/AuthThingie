package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, AnyContentAsFormUrlEncoded, ControllerComponents, Request}
import services.DuoSecurity

@Singleton
class DuoController @Inject() (duoSecurity: DuoSecurity, cc: ControllerComponents) extends AbstractController(cc) {

  def sampleDuo = Action { implicit request =>

    val host = duoSecurity.HostUrl
    val sig = duoSecurity.signRequest("test")

    Ok(views.html.duo(host, sig, "/duo"))
  }

  def verifyDuo = Action { implicit request =>
    val sigResponse = request.body.asFormUrlEncoded.flatMap(_.get("sig_response").flatMap(_.headOption))

    sigResponse match {
      case Some(resp) =>
        val res = duoSecurity.verifyRequest(resp)
        Ok(s"got response $res")
      case None =>
        Unauthorized("no")
    }
  }
}
