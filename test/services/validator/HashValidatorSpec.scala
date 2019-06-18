package services.validator

import org.scalatestplus.play.PlaySpec

class HashValidatorSpec extends PlaySpec {

  "HashValidator" should {
    "handle bcrypt" in {
      HashValidator.validateHash("$2y$05$dZtAVchKWGSLT1UDS02okuV2mqlnrL9GTX2T6NMLhKlU5mJ6/zazS", "totally-secure-password") must be (true)
      HashValidator.validateHash("$2y$05$dZtAVchKWGSLT1UDS02okuV2mqlnrL9GTX2T6NMLhKlU5mJ6/zazS", "nope") must be (false)
    }

    "handle APR1" in {
      HashValidator.validateHash("$apr1$gS/QAjUs$VP3KXrw27e2ft2/5Fohqf1", "totally-secure-password") must be (true)
      HashValidator.validateHash("$apr1$gS/QAjUs$VP3KXrw27e2ft2/5Fohqf1", "nope") must be (false)
    }

    "handle SHA" in {
      HashValidator.validateHash("{SHA}ekV5HjKINmRRfd+iZ6Q6mxLquRI=", "totally-secure-password") must be (true)
      HashValidator.validateHash("{SHA}ekV5HjKINmRRfd+iZ6Q6mxLquRI=", "nope") must be (false)
    }

    "handle plaintext :(" in {
      HashValidator.validateHash("totally-secure-password", "totally-secure-password") must be (true)
      HashValidator.validateHash("totally-secure-password", "false") must be (false)
    }
  }

}
