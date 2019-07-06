package controllers

import config.AuthThingieConfig
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.Mockito._
import services.rules.PathRule
import services.users.{User, UserMatcher}

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with MockitoSugar {

  "HomeController GET" should {

    "render the login page when a logged out user is there" in {
      val fakeConfig = mock[AuthThingieConfig]
      when(fakeConfig.getUsers) thenReturn List()
      when(fakeConfig.getPathRules) thenReturn List()

      val fakeUserMatcher = mock[UserMatcher]

      val controller = new HomeController(fakeConfig, fakeUserMatcher, Helpers.stubMessagesControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) must include ("Click here to login")
    }

    "render some path rules and user info when logged in as admin" in {
      val fakeConfig = mock[AuthThingieConfig]
      when(fakeConfig.getUsers) thenReturn List(User("test:foo", admin = true, None, List()))
      when(fakeConfig.getPathRules) thenReturn List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))

      val fakeUserMatcher = mock[UserMatcher]
      when (fakeUserMatcher.getUser("test")) thenReturn Some(User("test:foo", admin = true, None, List()))

      val controller = new HomeController(fakeConfig, fakeUserMatcher, Helpers.stubMessagesControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) must include ("<h2>Users</h2>") //users header
      contentAsString(home) must include ("<h2>Path Rules</h2>") //path rules header

    }

    "render no path rules and no user info when not admin" in {
      val fakeConfig = mock[AuthThingieConfig]
      when(fakeConfig.getUsers) thenReturn List(User("test:foo", admin = false, None, List()))
      when(fakeConfig.getPathRules) thenReturn List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))

      val fakeUserMatcher = mock[UserMatcher]
      when (fakeUserMatcher.getUser("test")) thenReturn Some(User("test:foo", admin = false, None, List()))

      val controller = new HomeController(fakeConfig, fakeUserMatcher, Helpers.stubMessagesControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) mustNot include ("<h2>Users</h2>") //users header
      contentAsString(home) mustNot include ("<h2>Path Rules</h2>") //path rules header
    }
  }
}
