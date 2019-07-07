package controllers

import config.AuthThingieConfig
import org.mockito.IdiomaticMockito
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import services.rules.PathRule
import services.users.{User, UserMatcher}


class HomeControllerSpec extends PlaySpec with IdiomaticMockito {

  "HomeController GET" should {

    trait Setup {
      val fakeConfig = mock[AuthThingieConfig]
      val fakeUserMatcher = mock[UserMatcher]

      val controller = new HomeController(fakeConfig, fakeUserMatcher, Helpers.stubMessagesControllerComponents())
    }

    "render the login page when a logged out user is there" in new Setup() {
      fakeConfig.getUsers returns List[User]()
      fakeConfig.getPathRules returns List[PathRule]()

      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) must include ("Click here to login")

    }

    "render some path rules and user info when logged in as admin" in new Setup() {
      fakeConfig.getUsers returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.getPathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) must include ("<h2>Users</h2>") //users header
      contentAsString(home) must include ("<h2>Path Rules</h2>") //path rules header

    }

    "render no path rules and no user info when not admin" in new Setup() {
      fakeConfig.getUsers returns List(User("test:foo", admin = false, None, List()))
      fakeConfig.getPathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = false, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to the auth thingie!")
      contentAsString(home) mustNot include ("<h2>Users</h2>") //users header
      contentAsString(home) mustNot include ("<h2>Path Rules</h2>") //path rules header
    }
  }
}
