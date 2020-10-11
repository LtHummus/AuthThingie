package services.rules

import java.time.Duration

import config.AuthThingieConfig
import org.joda.time.DateTime
import org.mockito.IdiomaticMockito
import org.scalatestplus.play.PlaySpec

class PathSpec extends PlaySpec with IdiomaticMockito {

  private val allOnTestExampleCom = PathRule("all on test.example.com", None, Some("test.example.com"), None, public = false, List())
  private val binStarOnAnything = PathRule("/bin* on anything", None, None, Some("/bin*"), public = false, List())
  private val barOnFooExampleCom = PathRule("Exactly /bar on foo.example.com", None, Some("foo.example.com"), Some("/bar"), public = true, List())
  private val bStarOnFooExampleCom = PathRule("/bar* on foo.example.com", None, Some("foo.example.com"), Some("/b*"), public = false, List())

  private val exampleWithTimeout = PathRule("some test", None, None, None, false, List(), Some(Duration.ofHours(1)))

  private val fakeConfig = mock[AuthThingieConfig]
  fakeConfig.pathRules returns List(allOnTestExampleCom, binStarOnAnything, barOnFooExampleCom, bStarOnFooExampleCom)

  private val matcher = new PathMatcher(fakeConfig)

  "PathMatcher" should {
    "do a basic match" in {
      matcher.getRule("https", "test.example.com", "/index.html") must be (Some(allOnTestExampleCom))
    }

    "do matches in order" in {
      matcher.getRule("https", "foo.example.com", "/bar") must be (Some(barOnFooExampleCom))
      matcher.getRule("https", "foo.example.com", "/ba") must be (Some(bStarOnFooExampleCom))
    }

    "return none for no matches" in {
      matcher.getRule("https", "nothing.example.com", "/") must be (None)
    }

  }

  "PathRule" should {
    "properly match components" in {
      barOnFooExampleCom.matches("https", "foo.example.com", "/bar") must be (true)
      barOnFooExampleCom.matches("https", "foo.example.com", "/barrrrr") must be (false)
    }

    "properly ignore unspecified components" in {
      allOnTestExampleCom.matches("https", "test.example.com", "/") must be (true)
      allOnTestExampleCom.matches("https", "test.example.com", "/abc") must be (true)
      allOnTestExampleCom.matches("http", "test.example.com", "/abc/def") must be (true)
      allOnTestExampleCom.matches("https", "nottest.example.com", "/") must be (false)

      binStarOnAnything.matches("http", "abcdefg", "/binabc") must be (true)
      binStarOnAnything.matches("http", "abcdefg", "/aa") must be (false)
    }

    "properly handle timeframes with no timeout" in {
      exampleWithTimeout.withinTimeframe(None) must be (false) // no time is always false
      exampleWithTimeout.withinTimeframe(Some(DateTime.now().minusHours(2))) must be (false) // before allowed time
      exampleWithTimeout.withinTimeframe(Some(DateTime.now().minusMinutes(30))) must be (true) // after allowed time

      allOnTestExampleCom.withinTimeframe(Some(DateTime.now().minusYears(1))) must be (true) // no duration always true
    }
  }

}
