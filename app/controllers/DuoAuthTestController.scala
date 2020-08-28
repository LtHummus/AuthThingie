package controllers

import java.time.Duration

import akka.actor.{ActorRef, ActorSystem}
import javax.inject.{Inject, Named, Singleton}
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}
import services.duo.{DuoAsyncActor, DuoAsyncAuthStatus, DuoWebAuth, SyncAuthResult}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import config.AuthThingieConfig
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import services.hmac.HmacUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

@Singleton
class DuoAuthTestController @Inject()(duo: DuoWebAuth, config: AuthThingieConfig, cc: ControllerComponents)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer) extends AbstractController(cc) {

  private val TestUsername = "test"
  private val PartialAuthUsername = "partialAuthUsername"


  def ping = Action.async {
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


  def socketHome = Action.async { request =>
    for {
      data <- duo.preauth(TestUsername)
    } yield {
      Ok(views.html.socket_test(data));
    }
  }

  def sendPush = Action.async{ request =>
    val deviceName = request.queryString("device").head

    for {
      txid <- duo.authAsync(TestUsername, "push", deviceName)
    } yield {
      Ok(Json.obj("txId" -> txid.txid))
    }
  }

  def streamTest = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      DuoAsyncActor.props(out, duo, request.queryString("txId").head, "https://test.example.com", TestUsername, config.timeZone)
    }
  }

  def redirect = Action { request =>
    val payload = DuoAsyncAuthStatus(request.queryString("key").head)
    val signatureValid = HmacUtils.validate(payload.signaturePayload, payload.signature)

    // time to do a bunch of checks
    if (!signatureValid) {
      Forbidden(Json.obj("error" -> "invalid signature"))
    } else if (payload.timeSinceSignature(config.timeZone).compareTo(Duration.ofSeconds(60)) > 0) {
      Forbidden(Json.obj("error" -> "took too long"))
    } else if (payload.username != request.session.get(PartialAuthUsername).getOrElse(TestUsername)) { // TODO: fix this
      Forbidden(Json.obj("error" -> "mismatched username"))
    } else if (payload.status != "allow") {
      //TODO: more sane deny
      Redirect(routes.HomeController.index(), FOUND)
    } else {
      // TODO: log in here
      Redirect(payload.redirectUrl, FOUND)
    }
  }
}
