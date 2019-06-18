package controllers

import config.AuthThingieConfig
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.Mockito._
import services.rules.PathRule
import services.users.{User, UserMatcher}

class LoginControllerSpec extends PlaySpec with MockitoSugar {

  "LoginController" should {
    "validate login info" in {

    }
  }
}
