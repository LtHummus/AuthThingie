package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import config.AuthThingieConfig
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}
import services.duo.{DuoAsyncActor, DuoWebAuth}
import services.ticket.EntryTicketService
import util.Constants._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DuoController @Inject()(duoWebAuth: DuoWebAuth,
                              ticketService: EntryTicketService,
                              config: AuthThingieConfig,
                              cc: ControllerComponents)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer) extends AbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  def sendPush = Action.async { request =>
    val deviceName = request.queryString("device").head

    for {
      txid <- duoWebAuth.authAsync(request.session(PartialAuthUsername), "push", deviceName, request.headers.get(XForwardedFor))
    } yield {
      Ok(Json.obj("txId" -> txid.txid))
    }
  }

  def duoPushStatus = WebSocket.acceptOrResult[String, String] { request =>
    Future.successful((request.queryString.get("txId"), request.queryString.get("redirectUrl"), request.session.get(PartialAuthUsername)) match {
      case (Some(txId), Some(ru), Some(pau)) => Right(
        ActorFlow.actorRef { out =>
          DuoAsyncActor.props(out, duoWebAuth, txId.head, ru.head, pau, config.timeZone, ticketService)
        }
      )
      case _ =>
        Logger.warn(s"Missing a param when attempting to listen to duo auth request. TXID = ${request.queryString.get("txId")}, redirect = ${request.queryString.get("redirectUrl")}, PAU = ${request.session.get(PartialAuthUsername)}")
        Left(BadRequest(s"Missing a param"))
    }

    )
  }
}
