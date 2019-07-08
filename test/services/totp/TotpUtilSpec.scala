package services.totp

import org.scalatestplus.play.PlaySpec
import scala.concurrent.duration._

class TotpUtilSpec extends PlaySpec {
  private val Secret = "T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"
  private val Time = 1562531180143L //648026
  private val PrevTime = Time - 30.seconds.toMillis //101297
  private val LateTime = Time + 30.seconds.toMillis //791466

  "TotpUtil" should {
    "generate a known code" in {
      TotpUtil.genOneTimePassword(Secret, Time) mustBe "648026"
    }

    "properly deal with leniency" in {
      TotpUtil.validateOneTimePassword(Secret, "648026", startInstant = Time) mustBe true
      TotpUtil.validateOneTimePassword(Secret, "101297", startInstant = Time) mustBe true
      TotpUtil.validateOneTimePassword(Secret, "791466", startInstant = Time) mustBe true

      //one code before valid
      TotpUtil.validateOneTimePassword(Secret, "699317", startInstant = Time) mustBe false

      //one code after valid
      TotpUtil.validateOneTimePassword(Secret, "662059", startInstant = Time) mustBe false
    }
  }
}
