package controllers

import config.AuthThingieConfig
import org.mockito.IdiomaticMockito
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.Session
import services.totp.TotpUtil
import services.users.{User, UserMatcher}
import scala.concurrent.duration._

class LoginControllerSpec extends PlaySpec with IdiomaticMockito {
  private val TimeTolerance = 500.millis

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
      redirectLocation(result) mustBe Some("http://foo.example.com")
      val returnedSession = session(result)
      returnedSession.get("user") mustBe Some("user")

      val authedTime = returnedSession.get("authTime").getOrElse(fail("no auth time returned")).toLong

      authedTime mustBe < (System.currentTimeMillis() + TimeTolerance.toMillis)
      authedTime mustBe > (System.currentTimeMillis() - TimeTolerance.toMillis)

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
      contentAsString(result) must include("Invalid username or password")
      session(result).get("user").isEmpty mustBe true
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
      contentAsString(result) must include("Error: No partially authed username.")
    }

    "correctly reject incorrect totp code" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> "000000")))

      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("Invalid Auth Code")
    }

    "correctly validate totp code" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis
      val correctCode = TotpUtil.genOneTimePassword("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH", System.currentTimeMillis())

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> correctCode)))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("someUrl")

      val returnedSession = session(result)

      returnedSession.get("user") mustBe(Some("test"))

      val authedTime = returnedSession.get("authTime").getOrElse(fail("no auth time set")).toLong
      authedTime mustBe < (System.currentTimeMillis() + TimeTolerance.toMillis)
      authedTime mustBe > (System.currentTimeMillis() - TimeTolerance.toMillis)


    }

    "respect auth timeout" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List()))

      val authExpiration = System.currentTimeMillis() - 5.minutes.toMillis
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
