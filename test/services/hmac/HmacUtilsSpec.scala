package services.hmac

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

class HmacUtilsSpec extends PlaySpec with MockitoSugar{

  "hmacUtils" should {
    "correctly sign and validate things" in {
      val res = HmacUtils.sign("abc")

      HmacUtils.validate("abc", res) mustBe true
      HmacUtils.validate("abd", res) mustBe false
      HmacUtils.validate("abc", "a" + res.tail) mustBe false

      // if this line of code fails, buy a lottery ticket
      HmacUtils.validate("abc", "R_8HCTpDYI7CPA8TytwN-F5Tk0NhSZms8ijA8jbFDiA=") mustBe false
    }
  }
}
