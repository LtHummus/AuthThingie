package config

import cats.data.{Validated, ValidatedNec}
import cats.data.Validated.Valid
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import services.rules.PathRule
import services.users.User

import scala.util.{Failure, Success, Try}

@Singleton
class AuthThingieConfig @Inject() (baseConfig: Configuration) {

  type ValidationResult[T] = ValidatedNec[String, T]

  private implicit class RichTry[T](x: Try[T]) {
    def toValidated: ValidationResult[T] = x match {
      case Failure(e) => s"${e.getClass.getSimpleName}:${e.getMessage}".invalidNec
      case Success(value) => value.validNec
    }
  }

  private def loadPathRules: ValidationResult[List[PathRule]] = {
    Try(baseConfig.getDeprecated[List[PathRule]]("auththingie.rules", "rules")).toValidated
  }

  private def loadUsers: ValidationResult[List[User]] = {
    Try(baseConfig.getDeprecated[List[User]]("auththingie.users", "users")).toValidated
  }

  private def loadForceRedirect: ValidationResult[Boolean] = {
    Valid(baseConfig.getOptional[Boolean]("auththingie.forceRedirectToHttps").contains(true))
  }

  private val loadSiteUrl: ValidationResult[String] = {
    Try(baseConfig.getDeprecated[String]("auththingie.authSiteUrl", "auth_site_url", "authSiteUrl")).toValidated
  }

  private val loadSiteName: ValidationResult[String] = {
    Try(baseConfig.getOptional[String]("auththingie.siteName").getOrElse("AuthThingie")).toValidated
  }

  private val Logger = play.api.Logger(this.getClass)

  case class AuthThingieConfig(rules: List[PathRule], users: List[User], forceRedirectToHttps: Boolean, siteUrl: String, siteName: String)

  private val parsedConfig = {
    (loadPathRules, loadUsers, loadForceRedirect, loadSiteUrl, loadSiteName).mapN(AuthThingieConfig)
  }

  val (pathRules, users, forceHttpsRedirect, siteUrl, siteName) = parsedConfig match {
    case Valid(a) =>
      Logger.info("Valid configuration parsed and loaded")
      (a.rules, a.users, a.forceRedirectToHttps, a.siteUrl, a.siteName)
    case Validated.Invalid(e) =>
      Logger.warn("Invalid configuration!")
      (List(), List(), false, "", "")
  }

  val isUsingNewConfig: Boolean = baseConfig.has("auththingie.users")
  val configErrors: List[String] = parsedConfig match {
    case Valid(_)                  => List()
    case Validated.Invalid(errors) => errors.toList
  }

  if (!isUsingNewConfig) {
    Logger.warn(" Hi there! A major change was made to the way AuthThingie loads configuration. You're still using the old-style method. " +
      "You should probably change that. Checkout the README at https://github.com/LtHummus/AuthThingie for information on how to get up to date")
  }

}
