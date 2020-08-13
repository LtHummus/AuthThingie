package config

import java.time.temporal.TemporalAmount
import java.time.{Duration, ZoneId}

import cats.data.{Validated, ValidatedNec}
import cats.data.Validated.Valid
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.mvc.Http.HeaderNames
import services.rules.PathRule
import services.users.User
import util.RichDuration.PrettyPrintableDuration

import scala.util.{Failure, Success, Try}

@Singleton
class AuthThingieConfig @Inject() (baseConfig: Configuration) {
  private val PlaySessionExpirationPath = "play.http.session.maxAge"
  private val OneYear = "365d"

  type ValidationResult[T] = ValidatedNec[String, T]

  private implicit class RichTry[T](x: Try[T]) {
    def toValidated: ValidationResult[T] = x match {
      case Failure(e) => s"${e.getClass.getSimpleName}:${e.getMessage}".invalidNec
      case Success(value) => value.validNec
    }
  }

  private def loadPathRules: ValidationResult[List[PathRule]] = {
    Try(baseConfig.getDeprecated[List[PathRule]]("auththingie.rules", "rules").map(rule => {
      rule.timeout match {
        case None    => rule.copy(timeout = Some(baseConfig.underlying.getDuration(PlaySessionExpirationPath)))
        case Some(_) => rule
      }
    })).toValidated
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

  private val authHeaderName: ValidationResult[String] = {
    Try(baseConfig.getOptional[String]("auththingie.authHeader").getOrElse(HeaderNames.AUTHORIZATION)).toValidated
  }

  private val readTimeZone: ValidationResult[ZoneId] = {
    Try(baseConfig.getOptional[String]("auththingie.timeZone").map(ZoneId.of).getOrElse(ZoneId.systemDefault())).toValidated
  }

  private val loadTimeout: ValidationResult[Duration] = {
    val auththingieTimeout = baseConfig.getOptional[TemporalAmount]("auththingie.timeout")
    val playMaxAge = baseConfig.getOptional[TemporalAmount]("play.http.session.maxAge")
    val playJwtExpire = baseConfig.getOptional[TemporalAmount]("play.http.session.jwt.expiresAfter")
    Try(List(auththingieTimeout, playMaxAge, playJwtExpire).flatten.head).map(Duration.from).toValidated
  }

  private val Logger = play.api.Logger(this.getClass)

  case class AuthThingieConfig(rules: List[PathRule], users: List[User], forceRedirectToHttps: Boolean, siteUrl: String, siteName: String, headerName: String, timeZone: ZoneId, sessionTimeout: Duration)

  private val parsedConfig = {
    (loadPathRules, loadUsers, loadForceRedirect, loadSiteUrl, loadSiteName, authHeaderName, readTimeZone, loadTimeout).mapN(AuthThingieConfig)
  }

  val (pathRules, users, forceHttpsRedirect, siteUrl, siteName, headerName, timeZone, sessionTimeout) = parsedConfig match {
    case Valid(a) =>
      Logger.info("Valid configuration parsed and loaded")
      (a.rules, a.users, a.forceRedirectToHttps, a.siteUrl, a.siteName, a.headerName, a.timeZone, a.sessionTimeout)
    case Validated.Invalid(e) =>
      Logger.warn("Invalid configuration!")
      (List(), List(), false, "", "", "", ZoneId.systemDefault(), Duration.ofDays(1))
  }

  val hasTimeoutSetProperly: Boolean = baseConfig.getOptional[String](PlaySessionExpirationPath).contains(OneYear)
  val isUsingNewConfig: Boolean = baseConfig.has("auththingie.users") || !hasTimeoutSetProperly
  val configErrors: List[String] = parsedConfig match {
    case Valid(_)                  => List()
    case Validated.Invalid(errors) => errors.toList
  }

  def asMap: Map[String, String] = {
    Map(
      "Site URL" -> siteUrl,
      "Site Name" -> siteName,
      "Header Name" -> headerName,
      "Time Zone" -> timeZone.toString,
      "Session Timeout" -> sessionTimeout.prettyPrint
    )
  }

  if (!isUsingNewConfig) {
    Logger.warn(" Hi there! A major change was made to the way AuthThingie loads configuration. You're still using the old-style method. " +
      "You should probably change that. Checkout the README at https://github.com/LtHummus/AuthThingie for information on how to get up to date")
  }

}
