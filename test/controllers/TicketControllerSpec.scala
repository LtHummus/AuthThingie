package controllers

import org.mockito.IdiomaticMockito
import org.scalatestplus.play.PlaySpec
import play.api.test._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import services.ticket.{EntryTicket, EntryTicketService}

import scala.concurrent.duration.DurationInt

class TicketControllerSpec extends PlaySpec with IdiomaticMockito {

  private val TimeTolerance = 500.millis

  trait Setup {
    val fakeTicketService = mock[EntryTicketService]
    val fakeComponents = Helpers.stubControllerComponents()

    val controller = new TicketController(fakeTicketService, fakeComponents)
  }

  "Ticket Validation" should {
    "accept a valid ticket" in new Setup() {
      fakeTicketService.retrieveTicket("abcdefg") returns Some(EntryTicket("ok", "test", "https://example.com", 12345L))

      val result = controller.validateTicket().apply(FakeRequest(GET, "/ticket?ticket=abcdefg"))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("https://example.com")

      session(result).get("user") mustBe Some("test")
      session(result).get("authTime").map(_.toLong).get mustBe System.currentTimeMillis() +- TimeTolerance.toMillis
    }

    "fail if no ticket specified" in new Setup() {
      val result = controller.validateTicket().apply(FakeRequest(GET, "/ticket"))

      status(result) mustBe FORBIDDEN
      session(result).get("user") mustBe None
    }

    "fail if ticket does not exist" in new Setup() {
      fakeTicketService.retrieveTicket("does-not-exist") returns None

      val result = controller.validateTicket().apply(FakeRequest(GET, "/ticket?ticket=does-not-exist"))

      status(result) mustBe FORBIDDEN
      session(result).get("user") mustBe None
    }
  }
}
