package services.users

import config.AuthThingieConfig
import org.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.rules.PathRule

class UserSpec extends PlaySpec with MockitoSugar {

  private val TestUser = User("test:$2y$05$x64wEIDNwkjRvbfRPheBfOa4E0.Z/V64Gu0aorNu5iEhsLmoNxCRq", admin = false, None, List("a", "b"))
  private val AdminUser = User("admin:$2y$05$ktjBjGePZrol6bMrLKaWJOrK3a3xfX.ju1JCmxTowQnmr4VaT6xTC", admin = true, None, List())

  private val PublicTestRule = PathRule("abc", None, None, None, public = true, List())
  private val PrivateATestRule = PathRule("abc", None, None, None, public = false, List("a"))
  private val PrivateZTestRule = PathRule("abc", None, None, None, public = false, List("z"))

  private val FakeConfig = mock[AuthThingieConfig]
  when(FakeConfig.getUsers) thenReturn List(TestUser, AdminUser)

  private val UserMatcher = new UserMatcher(FakeConfig)

  "User" should {
    "allow public rules" in {
      TestUser.isPermitted(PublicTestRule) must be (true)
      AdminUser.isPermitted(PublicTestRule) must be (true)
    }

    "only allow non-admins access to their roles" in {
      TestUser.isPermitted(PrivateATestRule) must be (true)
      TestUser.isPermitted(PrivateZTestRule) must be (false)
    }

    "validate passwords" in {
      TestUser.passwordCorrect("test") must be (true)
      TestUser.passwordCorrect("no") must be (false)

      AdminUser.passwordCorrect("hello") must be (true)
      AdminUser.passwordCorrect("iello") must be (false)
    }
  }

  "UserMatcher" should {
    "validate user passwords" in {
      UserMatcher.validUser("test", "test") must be (Some(TestUser))
      UserMatcher.validUser("admin", "hello") must be (Some(AdminUser))
      UserMatcher.validUser("test", "hello") must be (None)
      UserMatcher.validUser("notexist", "aa") must be (None)
    }

    "properly pull users from map" in {
      UserMatcher.getUser("test") must be (Some(TestUser))
      UserMatcher.getUser("admin") must be (Some(AdminUser))
      UserMatcher.getUser("notexist") must be (None)
    }
  }

}
