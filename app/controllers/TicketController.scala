package controllers

import play.api.libs.json.Json
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.ticket.EntryTicketService

import javax.inject.{Inject, Singleton}

@Singleton
class TicketController @Inject() (ticketService: EntryTicketService, cc: ControllerComponents) extends AbstractController(cc) {
  def validateTicket = Action { implicit request: Request[AnyContent] =>
    val potentialTicket = for {
      ticketId <- request.getQueryString("ticket")
      ticket   <- ticketService.retrieveTicket(ticketId)
    } yield {
      ticket
    }

    potentialTicket match {
      case None =>
        Forbidden(views.html.denied("Invalid ticket specified"))
      case Some(ticket) =>
        Redirect(ticket.redirectUrl, FOUND).withSession("user" -> ticket.username, "authTime" -> System.currentTimeMillis().toString)
    }
  }
}