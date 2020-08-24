package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, ControllerComponents}
import services.duo.DuoWebAuth

import scala.concurrent.ExecutionContext

@Singleton
class DuoAuthTestController @Inject() (duo: DuoWebAuth, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val TestUsername = "test"

  def ping = Action.async{
    for {
      preauthResult <- duo.preauth(TestUsername)
      authId <- duo.authAsync(TestUsername, "push", preauthResult.devices.head.device)
      _ = println(authId)
      ar1 <- duo.authStatus(authId.txid)
      _ = println(ar1)
      authResult <- duo.authStatus(authId.txid)
      _ = println(authResult)
    } yield {
      Ok(authResult.toString)
    }
  }
}
