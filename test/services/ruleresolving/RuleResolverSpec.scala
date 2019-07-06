package services.ruleresolving

import org.scalatestplus.play.PlaySpec
import services.rules.PathRule
import services.users.User

class RuleResolverSpec extends PlaySpec {

  val publicRule = PathRule("test", None, None, None, public = true, List())
  val privateRuleWithA = PathRule("foo", None, None, None, public = false, List("a"))
  val userWithNone = User("a:q", admin = false, None, List())
  val userWithA = User("a:b", admin = false, None, List("a"))
  val userWithB = User("a:c", admin = false, None, List("b"))
  val admin = User("b:c", admin = true, None, List())

  val resolver = new RuleResolver

  "RuleResolver" should {
    "allow access to public resources regardless of login status" in {
      resolver.resolveUserAccessForRule(None, Some(publicRule)) must be(Allowed)
      resolver.resolveUserAccessForRule(Some(userWithNone), Some(publicRule)) must be(Allowed)
      resolver.resolveUserAccessForRule(Some(userWithA), Some(publicRule)) must be(Allowed)
      resolver.resolveUserAccessForRule(Some(admin), Some(publicRule)) must be(Allowed)
    }

    "deny access to logged out users if path is not public" in {
      resolver.resolveUserAccessForRule(None, Some(privateRuleWithA)) must be (Denied)
    }

    "deny access to logged out user if no rule matches" in {
      resolver.resolveUserAccessForRule(None, None) must be (Denied)
    }

    "no rules found must be admin only" in {
      resolver.resolveUserAccessForRule(Some(userWithNone), None) must be (Denied)
      resolver.resolveUserAccessForRule(Some(userWithA), None) must be (Denied)
      resolver.resolveUserAccessForRule(Some(userWithB), None) must be (Denied)
      resolver.resolveUserAccessForRule(Some(admin), None) must be (Allowed)
    }

    "follow order of rules" in {
      resolver.resolveUserAccessForRule(Some(userWithNone), Some(privateRuleWithA)) must be (Denied)
      resolver.resolveUserAccessForRule(Some(userWithA), Some(privateRuleWithA)) must be (Allowed)
      resolver.resolveUserAccessForRule(Some(userWithB), Some(privateRuleWithA)) must be (Denied)
      resolver.resolveUserAccessForRule(Some(admin), Some(privateRuleWithA)) must be (Allowed)
    }
  }
}
