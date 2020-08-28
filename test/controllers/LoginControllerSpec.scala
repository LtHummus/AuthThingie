package controllers

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.duosecurity.duoweb.DuoWebException
import config.{AuthThingieConfig, DuoSecurityConfig}
import org.mockito.IdiomaticMockito
import org.scalatestplus.play._
import org.scalatest.OptionValues._
import play.api.Play.materializer
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.Session
import services.duo.{DuoSecurity, DuoWebAuth, PreAuthResponse}
import services.totp.TotpUtil
import services.users.{User, UserMatcher}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class LoginControllerSpec extends PlaySpec with IdiomaticMockito {
  private val TimeTolerance = 500.millis

  trait Setup {
    val fakeUserMatcher = mock[UserMatcher]
    val fakeComponents = Helpers.stubMessagesControllerComponents()
    val fakeConfig = mock[AuthThingieConfig]
    val fakeDuo = mock[DuoWebAuth]

    implicit val actorSystem = ActorSystem("test")
    implicit val executionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

    val controller = new LoginController(fakeConfig, fakeUserMatcher, fakeDuo, fakeComponents)

  }

  "Normal login flow" should {
    "validate login info" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("user:pass", admin = true, None, List(), duoEnabled = false))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=http://foo.example.com")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass")))


      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("http://foo.example.com")
      val returnedSession = session(result)
      returnedSession.get("user") mustBe Some("user")

      returnedSession.get("authTime").flatMap(_.toLongOption).value mustBe System.currentTimeMillis() +- TimeTolerance.toMillis
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
      session(result).get("user") mustBe None
    }
  }

  "TOTP flow" should {
    "redirect to totp page when needed" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = false))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value must startWith ("/totp?")
    }

    "redirect to totp page when duo enabled" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("test:test", admin = true, None, List(), duoEnabled = true))

      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value must startWith ("/totp?")
    }

    "not redirect to totp page when not needed" in new Setup() {
      fakeUserMatcher.validUser("user", "pass") returns Some(User("test:test", admin = true, None, List(), duoEnabled = false))

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

    "properly draw both forms when needed" in new Setup() {
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ-key", "security-key", "api-host"))
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = true))
      fakeDuo.preauth("test") returns Future.successful(PreAuthResponse("auth", "Auth ok", List()))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis
      val result = controller.showTotpForm().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")))

      status(result) mustBe OK
      contentAsString(result) must include("<input type=\"text\" id=\"totpCode\" name=\"totpCode\" value=\"\" required=\"true\" class=\"form-control\" size=\"10\" autofocus=\"autofocus\">")
      contentAsString(result) must include("<label for=\"device\">Select a device for push notifications:</label>")
    }

    "only draw TOTP form if user is not duo enabled" in new Setup() {
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ-key", "security-key", "api-host"))
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = false))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis
      val result = controller.showTotpForm().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")))

      status(result) mustBe OK
      contentAsString(result) must include("<input type=\"text\" id=\"totpCode\" name=\"totpCode\" value=\"\" required=\"true\" class=\"form-control\" size=\"10\" autofocus=\"autofocus\">")
      contentAsString(result) must not include("#duo_iframe")
    }

    "only draw Duo form if user is not TOTP enabled" in new Setup() {
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ-key", "security-key", "api-host"))
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, None, List(), duoEnabled = true))
      fakeDuo.preauth("test") returns Future.successful(PreAuthResponse("auth", "Auth ok", List()))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis
      val result = controller.showTotpForm().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")))

      status(result) mustBe OK
      contentAsString(result) must not include("<input type=\"text\" id=\"totpCode\" name=\"totpCode\" value=\"\" required=\"true\" class=\"form-control\" size=\"10\" autofocus=\"autofocus\">")
      contentAsString(result) must include("<label for=\"device\">Select a device for push notifications:</label>")
    }

    "correctly reject incorrect totp code" in new Setup() {
      fakeConfig.duoSecurity returns None
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = false))

      val authExpiration = System.currentTimeMillis() + 5.minutes.toMillis

      val result = controller.totp().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/totp?redirect=someUrl")
        .withSession("partialAuthUsername" -> "test", "partialAuthTimeout" -> authExpiration.toString)
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("totpCode" -> "000000")))

      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("Invalid Auth Code")
    }

    "correctly validate totp code" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = false))

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

      returnedSession.get("authTime").flatMap(_.toLongOption).value mustBe System.currentTimeMillis() +- TimeTolerance.toMillis
    }

    "respect auth timeout" in new Setup() {
      fakeUserMatcher.getUser("test") returns Some(User("test:test", admin = true, Some("T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"), List(), duoEnabled = false))

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

      status(result) mustBe SEE_OTHER
      session(result) mustBe Session()
    }
  }
}
