package services.duo

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import play.api.libs.json.Json
import services.hmac.HmacUtils

object DuoAsyncActor {
  def props(out: ActorRef, duoClient: DuoWebAuth, txId: String, redirectUrl: String, username: String, timeZone: ZoneId): Props = Props(new DuoAsyncActor(out, duoClient, txId, redirectUrl, username, timeZone))
}

class DuoAsyncActor(out: ActorRef, duoClient: DuoWebAuth, txId: String, redirectUrl: String, username: String, timeZone: ZoneId) extends Actor {
  import context.dispatcher

  private def computeSignature(instant: ZonedDateTime, res: SyncAuthResult): String = {
    val payload = s"$redirectUrl\n$username\n${instant.toInstant.toEpochMilli}\n${res.status}\n${res.result}"
    HmacUtils.sign(payload)
  }

  override def receive: Receive = {
    case msg: String =>
      if (msg == "die") {
        self ! PoisonPill
      } else {
        duoClient.authStatus(txId).map{ res =>
          if (res.result == "waiting") {
            out ! Json.toJson(res).toString()
          } else {
            val instant = ZonedDateTime.now(timeZone)
            val duoRes = DuoAsyncAuthStatus(res, username, redirectUrl, instant, computeSignature(instant, res))
            val json = Json.toJson(duoRes).toString()

            out ! json
          }
        }
      }

  }
}