package services.rules

import config.AuthThingieConfig
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

import org.mockito.Mockito._

class PathSpec extends PlaySpec with MockitoSugar {

  private val allOnTestExampleCom = PathRule("all on test.example.com", None, Some("test.example.com"), None, public = false, List())
  private val binStarOnAnything = PathRule("/bin* on anything", None, None, Some("/bin*"), public = false, List())
  private val barOnFooExampleCom = PathRule("Exactly /bar on foo.example.com", None, Some("foo.example.com"), Some("/bar"), public = true, List())
  private val bStarOnFooExampleCom = PathRule("/bar* on foo.example.com", None, Some("foo.example.com"), Some("/b*"), public = false, List())

  private val fakeConfig = mock[AuthThingieConfig]
  when(fakeConfig.getPathRules) thenReturn List(allOnTestExampleCom, binStarOnAnything, barOnFooExampleCom, bStarOnFooExampleCom)

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
  }

}
