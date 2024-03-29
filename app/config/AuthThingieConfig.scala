package config

import java.time.temporal.TemporalAmount
import java.time.{Duration, ZoneId}
import cats.data.{Validated, ValidatedNec}
import cats.data.Validated.Valid
import cats.syntax.all._
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
    def toRichValidated: ValidationResult[T] = x match {
      case Failure(e) => s"${e.getClass.getSimpleName}:${e.getMessage}".invalidNec
      case Success(value) => value.validNec
    }
  }

  private def loadPathRules: ValidationResult[List[PathRule]] = {
    Try(baseConfig.getDeprecated[List[PathRule]]("auththingie.rules", "rules")).toRichValidated
  }

  private def loadUsers: ValidationResult[List[User]] = {
    Try(baseConfig.getDeprecated[List[User]]("auththingie.users", "users")).toRichValidated
  }

  private def loadForceRedirect: ValidationResult[Boolean] = {
    Valid(baseConfig.getOptional[Boolean]("auththingie.forceRedirectToHttps").contains(true))
  }

  private val loadSiteUrl: ValidationResult[String] = {
    Try(baseConfig.getDeprecated[String]("auththingie.authSiteUrl", "auth_site_url", "authSiteUrl")).toRichValidated
  }

  private val loadSiteName: ValidationResult[String] = {
    Try(baseConfig.getOptional[String]("auththingie.siteName").getOrElse("AuthThingie")).toRichValidated
  }

  private val authHeaderName: ValidationResult[String] = {
    Try(baseConfig.getOptional[String]("auththingie.authHeader").getOrElse(HeaderNames.AUTHORIZATION)).toRichValidated
  }

  private val readTimeZone: ValidationResult[ZoneId] = {
    Try(baseConfig.getOptional[String]("auththingie.timeZone").map(ZoneId.of).getOrElse(ZoneId.systemDefault())).toRichValidated
  }

  private val loadTimeout: ValidationResult[Duration] = {
    val auththingieTimeout = baseConfig.getOptional[TemporalAmount]("auththingie.timeout")
    val playMaxAge = baseConfig.getOptional[TemporalAmount]("play.http.session.maxAge")
    val playJwtExpire = baseConfig.getOptional[TemporalAmount]("play.http.session.jwt.expiresAfter")
    Try(List(auththingieTimeout, playMaxAge, playJwtExpire).flatten.head).map(Duration.from).toRichValidated
  }

  private val loadDuoSecurity: ValidationResult[Option[DuoSecurityConfig]] = {
    Try(baseConfig.getOptional[DuoSecurityConfig]("auththingie.duo")).toRichValidated
  }

  private val Logger = play.api.Logger(this.getClass)

  case class AuthThingieConfig(rules: List[PathRule], users: List[User], forceRedirectToHttps: Boolean, siteUrl: String, siteName: String, headerName: String, timeZone: ZoneId, sessionTimeout: Duration, duoSecurity: Option[DuoSecurityConfig])

  private val parsedConfig = {
    (loadPathRules, loadUsers, loadForceRedirect, loadSiteUrl, loadSiteName, authHeaderName, readTimeZone, loadTimeout, loadDuoSecurity).mapN(AuthThingieConfig)
  }

  val (pathRules, users, forceHttpsRedirect, siteUrl, siteName, headerName, timeZone, sessionTimeout, duoSecurity) = parsedConfig match {
    case Valid(a) =>
      Logger.info("Valid configuration parsed and loaded")
      (a.rules, a.users, a.forceRedirectToHttps, a.siteUrl, a.siteName, a.headerName, a.timeZone, a.sessionTimeout, a.duoSecurity)
    case Validated.Invalid(e) =>
      Logger.warn(s"Invalid configuration: ${e.mkString_(", ")}")
      (List(), List(), false, "", "", "", ZoneId.systemDefault(), Duration.ofDays(1), None)
  }

  val hasTimeoutSetProperly: Boolean = baseConfig.getOptional[String](PlaySessionExpirationPath).contains(OneYear)
  val isUsingNewConfig: Boolean = baseConfig.has("auththingie.users") || !hasTimeoutSetProperly
  val configErrors: List[String] = parsedConfig match {
    case Valid(_)                  => List()
    case Validated.Invalid(errors) => errors.toList
  }

  def asEntries: List[(String, String)] = {
    List(
      "Site URL" -> siteUrl,
      "Site Name" -> siteName,
      "Header Name" -> headerName,
      "Time Zone" -> timeZone.toString,
      "Session Timeout" -> sessionTimeout.prettyPrint,
      "Duo Security Enabled" -> (if (duoSecurity.isDefined) "Yes" else "No")
    )
  }

  if (!isUsingNewConfig) {
    Logger.warn(" Hi there! A major change was made to the way AuthThingie loads configuration. You're still using the old-style method. " +
      "You should probably change that. Checkout the README at https://github.com/LtHummus/AuthThingie for information on how to get up to date")
  }

}
