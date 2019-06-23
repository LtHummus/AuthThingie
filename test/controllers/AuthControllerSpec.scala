package controllers

import config.AuthThingieConfig
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import services.decoding.{RequestDecoder, RequestInfo}
import services.ruleresolving.{Allowed, Denied, RuleResolver}
import services.rules.{PathMatcher, PathRule}
import services.users.{User, UserMatcher}


class AuthControllerSpec extends PlaySpec with MockitoSugar {

  "AuthController /auth" should {

    "properly let people in when the matcher says so" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeResolver.resolveUserAccessForRule(None, Some(pathRule))) thenReturn Allowed

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth"))

      status(result) mustBe NO_CONTENT
    }

    "redirect to error message if user is using basic auth and access is denied" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeUserMatcher.validUser("test", "test")) thenReturn Some(user)
      when(fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule))) thenReturn Denied

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("Authorization" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("You do not have permission for this resource")
    }

    "redirect to error message if user is logged in and access is denied" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeUserMatcher.getUser("test")) thenReturn Some(user)
      when(fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule))) thenReturn Denied

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "test"))

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("You do not have permission for this resource")
    }

    "redirect to login page if user is not logged in" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = false, List())
      val user = User("test:test", admin = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule))) thenReturn Denied

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth"))

      status(result) mustBe FOUND
      redirectLocation(result) mustBe Some("/needed?redirect=https%3A%2F%2Ftest.example.com%2F")
    }

    "properly decode basic auth headers" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = true, List())
      val user = User("test:test", admin = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeUserMatcher.validUser("test", "test")) thenReturn Some(user)
      when(fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule))) thenReturn Allowed

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth").withHeaders("Authorization" -> "Basic dGVzdDp0ZXN0"))

      status(result) mustBe NO_CONTENT
    }

    "properly decode session data" in {
      val fakeRequestDecoder = mock[RequestDecoder]
      val fakeUserMatcher = mock[UserMatcher]
      val fakePathMatcher = mock[PathMatcher]
      val fakeConfig = mock[AuthThingieConfig]
      val fakeResolver = mock[RuleResolver]
      val fakeComponents = Helpers.stubControllerComponents()

      val requestInfo = RequestInfo("https", "test.example.com", "/")
      val pathRule = PathRule("Some path", None, Some("test.example.com"), None, public = true, List())
      val user = User("test:test", admin = true, List())

      when(fakeRequestDecoder.decodeRequestHeaders(ArgumentMatchers.any())) thenReturn requestInfo
      when(fakePathMatcher.getRule(requestInfo)) thenReturn Some(pathRule)
      when(fakeUserMatcher.getUser("test")) thenReturn Some(user)
      when(fakeResolver.resolveUserAccessForRule(Some(user), Some(pathRule))) thenReturn Allowed

      val controller = new AuthController(fakeRequestDecoder, fakeUserMatcher, fakePathMatcher, fakeConfig, fakeResolver, fakeComponents)
      val result = controller.auth().apply(FakeRequest(GET, "/auth").withSession("user" -> "test"))

      status(result) mustBe NO_CONTENT
    }

  }
}
