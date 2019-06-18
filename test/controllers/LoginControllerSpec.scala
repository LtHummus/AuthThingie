package controllers

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.test._
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import org.mockito.Mockito._
import play.api.mvc.Session
import services.users.{User, UserMatcher}

class LoginControllerSpec extends PlaySpec with MockitoSugar {

  "Login Handler" should {
    "validate login info" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()

      when(fakeUserMatcher.validUser("user", "pass")) thenReturn Some(User("user:pass", admin = true, List()))

      val controller = new LoginController(fakeUserMatcher, fakeComponents)
      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrL")))


      status(result) mustBe FOUND
      session(result) mustBe Session(Map("user" -> "user"))
    }

    "reject invalid login info" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()

      when(fakeUserMatcher.validUser("user", "pass")) thenReturn None

      val controller = new LoginController(fakeUserMatcher, fakeComponents)
      val result = controller.login().apply(CSRFTokenHelper.addCSRFToken(FakeRequest(POST, "/login")
        .withHeaders("X-Forwarded-For" -> "127.0.0.1")
        .withFormUrlEncodedBody("username" -> "user",
          "password" -> "pass",
          "redirectUrl" -> "someUrL")))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
    }
  }


  "Logout Handler" should {
    "be able to logout" in {
      val fakeUserMatcher = mock[UserMatcher]
      val fakeComponents = Helpers.stubMessagesControllerComponents()
      val controller = new LoginController(fakeUserMatcher, fakeComponents)

      val result = controller.logout().apply(FakeRequest(GET, "/logout").withSession("user" -> "someone"))

      status(result) mustBe OK
      contentType(result) mustBe Some("text/plain")
      contentAsString(result) mustBe "Logged out"
      session(result) mustBe Session()
    }
  }
}
