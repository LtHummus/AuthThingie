package services.ticket

import org.scalatestplus.play.PlaySpec
import services.users.User

class EntryTicketServiceSpec extends PlaySpec {

  val service = new EntryTicketService

  "EntryTicketService" should {
    "create and store a ticket" in {
      val user = User("username:foo", admin = false, None, List(), duoEnabled = false)
      val ticketId = service.createTicket("some-result", user, "https://example.com")
      val retrievedTicket = service.retrieveTicket(ticketId)

      retrievedTicket mustBe defined

      retrievedTicket.get.result mustBe "some-result"
      retrievedTicket.get.username mustBe "username"
    }
  }

}
