package services.decoding

import org.scalatestplus.play.PlaySpec
import play.api.mvc.Headers

class RequestDecoderSpec extends PlaySpec {

  "RequestDecoder" should {
    "properly decode the complete request headers" in {
      val headers = Headers("X-Forwarded-Proto" -> "https", "X-Forwarded-Host" -> "test.example.com", "X-Forwarded-Uri" -> "/bin")

      val decoded = new RequestDecoder().decodeRequestHeaders(headers)

      decoded.protocol must be ("https")
      decoded.host must be ("test.example.com")
      decoded.path must be ("/bin")
    }

    "throw an error if something is missing" in {
      val headers = Headers("X-Forwarded-Host" -> "test.example.com", "X-Forwarded-Uri" -> "/bin")

      try {
        new RequestDecoder().decodeRequestHeaders(headers)
        fail("Should have thrown an exception")
      } catch {
        case e: IllegalArgumentException => e.getMessage must be("missing forwarding information")
      }


    }
  }
}
