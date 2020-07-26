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
      fakeConfig.users returns List[User]()
      fakeConfig.pathRules returns List[PathRule]()
      fakeConfig.siteName returns "AuthThingie"

      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("Click here to login")

    }

    "render some path rules and user info when logged in as admin" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("<h3>Users</h3>") //users header
      contentAsString(home) must include ("<h3>Rules</h3>") //path rules header

    }

    "render no path rules and no user info when not admin" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = false, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = false, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) mustNot include ("<h3>Users</h3>") //users header
      contentAsString(home) mustNot include ("<h3>Rules</h3>") //path rules header
    }

    "render public access properly" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = true, List()))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("<span class=\"badge badge-success\">Public Access</span>")
    }

    "render admin only properly" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = false, List()))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("<span class=\"badge badge-danger\">Admin Only</span>")
    }

    "render role tags properly on path rules" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = false, List("a", "b", "c")))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List()))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">a</span>")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">b</span>")
    }

    "render role tags properly on users" in new Setup() {
      fakeConfig.users returns List(User("test:foo", admin = true, None, List()))
      fakeConfig.pathRules returns List(PathRule("Test Rule", None, Some("test.example.com"), None, public = false, List("d", "e", "f", "g")))
      fakeConfig.siteName returns "AuthThingie"

      fakeUserMatcher.getUser("test") returns Some(User("test:foo", admin = true, None, List("a", "b", "c")))

      val home = controller.index().apply(FakeRequest(GET, "/").withSession("user" -> "test"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to AuthThingie!")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">d</span>")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">e</span>")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">f</span>")
      contentAsString(home) must include ("<span class=\"badge badge-primary\">g</span>")
    }
  }
}
