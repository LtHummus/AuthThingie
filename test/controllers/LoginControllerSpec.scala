package controllers

import config.AuthThingieConfig
import org.mockito.IdiomaticMockito
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.Session
import services.totp.TotpUtil
import services.users.{User, UserMatcher}

class LoginControllerSpec extends PlaySpec with IdiomaticMockito {
  trait Setup {
    val fakeUserMatcher = mock[UserMatcher]
    val fakeComponents = Helpers.stubMessagesControllerComponents()
    val fakeConfig = mock[AuthThingieConfig]

    val controller = new LoginController(fakeConfig, fakeUserMatcher, fakeComponents)

  }

  "Normal login flow" should {
    "validate login info" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("user:pass", admin = true, None, List()))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=http://foo.example.com")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass")))


      status(result) mustBe FOUND
      session(result) mustBe Session(Map("user" -> "user"))
      redirectLocation(result) mustBe Some("http://foo.example.com")
    }

    "reject invalid login info" in new Setup() {

      fakeUserMatcher.validUser("user", "pass") returns None

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
    }
  }

  "TOTP flow" should {
    "redirect to totp page when needed" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).getOrElse(fail("No redirect url")) must startWith ("/totp?")
    }

    "not redirect to totp page when not needed" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("test:test", admin = true, None, List()))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("someUrl")
    }

    "error when no partial auth is defined" in new Setup() {
      val result = controller.showTotpForm().apply(FakeRequest(GET, "/totp?redirect=someUrl"))

      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe "Error: No partially authed username."
    }

    "correctly reject incorrect totp code" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> "000000")))

      status(result) mustBe UNAUTHORIZED
    }

    "correctly validate totp code" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val authExpiration = System.currentTimeMillis() + (5 * 60 * 1000)
      val correctCode = TotpUtil.genOneTimePassword("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH", System.currentTimeMillis())

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> correctCode)))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("someUrl")
    }

    "respect auth timeout" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val authExpiration = System.currentTimeMillis() - (5 * 60 * 1000)
      val correctCode = TotpUtil.genOneTimePassword("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH", System.currentTimeMillis())

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> correctCode)))

      status(result) mustBe UNAUTHORIZED
    }
  }


  "Logout Handler" should {
    "be able to logout" in new Setup() {
      val result = controller.logout().apply(FakeRequest(GET, "/logout").withSession("user" -> "someone"))

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("Logged out")
      session(result) mustBe Session()
    }
  }
}
