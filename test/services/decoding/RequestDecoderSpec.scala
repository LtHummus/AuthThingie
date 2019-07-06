package services.decoding

import config.AuthThingieConfig
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Headers

class RequestDecoderSpec extends PlaySpec with MockitoSugar {

  "RequestDecoder" should {
    "properly decode the complete request headers" in {
      val headers = Headers("X-Forwarded-Proto" -> "https", "X-Forwarded-Host" -> "test.example.com", "X-Forwarded-Uri" -> "/bin")

      val mockConfig = mock[AuthThingieConfig]

      val decoded = new RequestDecoder(mockConfig).decodeRequestHeaders(headers)

      decoded.protocol must be ("https")
      decoded.host must be ("test.example.com")
      decoded.path must be ("/bin")
    }

    "throw an error if something is missing" in {
      val headers = Headers("X-Forwarded-Host" -> "test.example.com", "X-Forwarded-Uri" -> "/bin")

      val mockConfig = mock[AuthThingieConfig]

      try {
        new RequestDecoder(mockConfig).decodeRequestHeaders(headers)
        fail("Should have thrown an exception")
      } catch {
        case e: IllegalArgumentException => e.getMessage must be("missing forwarding information")
      }


    }
  }
}
