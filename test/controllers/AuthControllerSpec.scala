package controllers

import java.time.Duration
import java.time.temporal.TemporalUnit

import scala.concurrent.duration._
import config.AuthThingieConfig
import org.joda.time.DateTime
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}
import services.decoding.{RequestDecoder, RequestInfo}
import services.ruleresolving.{Allowed, Denied, RuleResolver}
import services.rules.{PathMatcher, PathRule}
import services.users.{User, UserMatcher}


class AuthControllerSpec extends PlaySpec with IdiomaticMockito with ArgumentMatchersSugar {

  trait Setup {
    val fakeRequestDecoder = mock[RequestDecoder]
    val fakeUserMatcher = mock[UserMatcher]
    val fakePathMatcher = mock[PathMatcher]
    val fakeConfig = mock[AuthThingieConfig]
    val fakeResolver = mock[RuleResolver]
    val fakeComponents = Helpers.stubControllerComponents()

    val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
  }

  "Normal auth flow" should {

    "properly let people in when the matcher says so" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("ben:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.getUser("ben") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "ben"))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.getUser("ben") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "redirect to error message if user is using basic auth and access is denied" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.validUser("test", "test") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Denied
      fakeConfig.headerName returns "Authorization"

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("Authorization" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("You do not have permission for this resource")

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.validUser("test", "test") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "redirect to error message if user is logged in and access is denied" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.getUser("test") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Denied

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "test"))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("You do not have permission for this resource")

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.getUser("test") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "redirect to login page if user is not logged in" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Denied

      val result = controller.auth().apply(FakeRequest(GET, "/auth"))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("/needed?redirect=https%3A%2F%2Ftest.example.com%2F")

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeResolver.resolveUserAccessForRule(None, Some(pathRule)) was called
    }

    "properly decode basic auth headers" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List(), Some(Duration.ofHours(1)))
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.validUser("test", "test") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed
      fakeConfig.headerName returns "Authorization"

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("Authorization" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.validUser("test", "test") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "properly decode basic auth headers with an arbitrary name" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.validUser("test", "test") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed
      fakeConfig.headerName returns "FooBarBaz"

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("FooBarBaz" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.validUser("test", "test") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "respect timeouts even with a valid session" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List(), Some(Duration.ofMinutes(1)))
      val user = User("ben:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.getUser("ben") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "ben", "authTime" -> DateTime.now().minusMinutes(10).getMillis.toString))

      status(result) mustBe FOUND

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.getUser("ben") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "work with a custom timeout" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List(), Some(Duration.ofMinutes(100)))
      val user = User("ben:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.getUser("ben") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "ben", "authTime" -> DateTime.now().minusMinutes(10).getMillis.toString))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.getUser("ben") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "properly decode session data" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.getUser("test") returns Some(user)
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) returns Allowed

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "test"))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.getUser("test") was called
      fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule)) was called
    }

    "don't bother looking at the user data if the rule is public" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = true, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)

      val result = controller.auth().apply(FakeRequest(GET, "/auth"))

      status(result) mustBe NO_CONTENT

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeResolver.resolveUserAccessForRule(None, Some(pathRule)) wasNever called
    }

    "show error if using basic auth and incorrect credentials" in new Setup() {
      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, None, List())

      fakeRequestDecoder.decodeRequestHeaders(*) returns requestInfo
      fakePathMatcher.getRule(requestInfo) returns Some(pathRule)
      fakeUserMatcher.validUser("test", "test") returns None
      fakeResolver.resolveUserAccessForRule(None, Some(pathRule)) returns Denied
      fakeConfig.headerName returns "Authorization"

      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("Authorization" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe UNAUTHORIZED

      fakeRequestDecoder.decodeRequestHeaders(*) wasCalled once
      fakePathMatcher.getRule(requestInfo) was called
      fakeUserMatcher.validUser("test", "test") was called
      fakeResolver.resolveUserAccessForRule(None, Some(pathRule)) was called
    }

  }
}
