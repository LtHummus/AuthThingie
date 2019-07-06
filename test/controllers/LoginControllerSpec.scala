package controllers

import config.AuthThingieConfig
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.Mockito._
import play.api.mvc.Session
import services.users.{User, UserMatcher}

class LoginControllerSpec extends PlaySpec with MockitoSugar {

  "Login Handler" should {
    "validate login info" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()
      val fakeConfig = mock[AuthThingieConfig]

      when(fakeUserMatcher.validUser("user", "pass")) thenReturn Some(User("user:pass", admin = true, None, List()))

      val controller = new LoginController(fakeConfig, fakeUserMatcher, fakeComponents)
      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=http://foo.example.com")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass")))


      status(result) mustBe FOUND
      session(result) mustBe Session(Map("user" -> "user"))
      redirectLocation(result) mustBe Some("http://foo.example.com")
    }

    "reject invalid login info" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()
      val fakeConfig = mock[AuthThingieConfig]

      when(fakeUserMatcher.validUser("user", "pass")) thenReturn None

      val controller = new LoginController(fakeConfig, fakeUserMatcher, fakeComponents)
      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login?redirect=someUrl")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrl")))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
    }
  }


  "Logout Handler" should {
    "be able to logout" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()
      val fakeConfig = mock[AuthThingieConfig]

      val controller = new LoginController(fakeConfig, fakeUserMatcher, fakeComponents)

      val result = controller.logout().apply(FakeRequest(GET, "/logout").withSession("user" -> "someone"))

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("Logged out")
      session(result) mustBe Session()
    }
  }
}
