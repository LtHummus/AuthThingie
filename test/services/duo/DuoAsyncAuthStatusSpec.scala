package services.duo

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.hmac.HmacUtils

class DuoAsyncAuthStatusSpec extends PlaySpec with MockitoSugar {

  "DuoAsyncAuthStatusSpec" should {
    "properly calculate a signature" in {
      val sample = DuoAsyncAuthStatus("allow", "allow", "ok", "username", "url", System.currentTimeMillis(), "")
      val signature = HmacUtils.sign(sample.signaturePayload)

      signature mustBe sample.withSignature.signature
      sample.validateSignature mustBe false
      sample.withSignature.validateSignature mustBe true
    }

    "changing input should change the signature" in {
      val sample = DuoAsyncAuthStatus("allow", "allow", "ok", "username", "url", System.currentTimeMillis(), "").withSignature

      sample.validateSignature mustBe true
      sample.copy(status = "foo").validateSignature mustBe false
      sample.copy(result = "foo").validateSignature mustBe false
      sample.copy(username = "username2").validateSignature mustBe false
      sample.copy(redirectUrl = "no").validateSignature mustBe false
      sample.copy(time = 1234L).validateSignature mustBe false
    }
  }
}
