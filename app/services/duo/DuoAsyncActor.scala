package services.duo

import java.time.{ZoneId, ZonedDateTime}
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import play.api.libs.json.Json
import services.hmac.HmacUtils
import services.ticket.EntryTicketService

object DuoAsyncActor {
  def props(out: ActorRef,
            duoClient: DuoWebAuth,
            txId: String,
            redirectUrl: String,
            username: String,
            timeZone: ZoneId,
            ticketService: EntryTicketService): Props = Props(new DuoAsyncActor(out, duoClient, txId, redirectUrl, username, timeZone, ticketService))
}

// TODO: this actor only works in response to events from the client instead of listening to Duo API itself...this should
//       actually just be an event stream, but i wasn't able to get that to work, so this is the work around we're using
//       for now. Some day, https://www.playframework.com/documentation/2.8.x/ScalaWebSockets#Handling-WebSockets-with-Akka-streams-directly
//       will work for us. But today is not that day.
class DuoAsyncActor(out: ActorRef, duoClient: DuoWebAuth, txId: String, redirectUrl: String, username: String, timeZone: ZoneId, ticketService: EntryTicketService) extends Actor {
  import context.dispatcher

  private val Logger = play.api.Logger(this.getClass)

  override def receive: Receive = {
    case msg: String =>
      if (msg == "die") {
        self ! PoisonPill
      } else {
        duoClient.authStatus(txId).map{ res =>
          if (res.result == "waiting") {
            out ! Json.toJson(res).toString()
          } else {
            val (ticket, message) = if (res.status == "allow") {
              val ticketId = ticketService.createTicket(res.result, username, redirectUrl)
              Logger.info(s"Storing ticket $ticketId")
              (ticketId, "Logging you in")
            } else {
              Logger.info(s"Ignoring result with status ${res.status}")
              (None, "Denied")
            }

            val json = Json.obj("status" -> res.status, "ticket" -> ticket, "status_msg" -> message).toString()

            out ! json
            self ! PoisonPill
          }
        }
      }

  }
}
