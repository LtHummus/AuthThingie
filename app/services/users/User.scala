package services.users

import java.util

import com.typesafe.config.Config
import org.apache.commons.codec.binary.Base64
import play.api.ConfigLoader
import services.rules.PathRule
import services.totp.TotpUtil
import services.validator.HashValidator

import scala.jdk.CollectionConverters._
import scala.util.Try

object User {
  implicit val configLoader: ConfigLoader[List[User]] = (config: Config, path: String) => {
    config.getConfigList(path).asScala.map { curr =>
      val passwdLine = curr.getString("htpasswdLine")
      val admin = Try(curr.getBoolean("admin")).getOrElse(false)
      val totpSecret = Try(curr.getString("totpSecret")).toOption
      val roles = Try(curr.getStringList("roles").asScala.toList).getOrElse(List.empty[String])
      val duoEnabled = Try(curr.getBoolean("duoEnabled")).getOrElse(false)

      User(passwdLine, admin, totpSecret, roles, duoEnabled)
    }.toList
  }
}

case class User(htpasswdLine: String, admin: Boolean, totpSecret: Option[String], roles: List[String], duoEnabled: Boolean) {

  private val Logger = play.api.Logger(this.getClass)

  val (username, passwordHash) = getCredentialParts

  private def getCredentialParts: (String, String) = {
    val parts = htpasswdLine.split(":", 2)
    require(parts.length == 2, "Invalid credentials in config")

    (parts(0), parts(1))
  }

  //note for later: should `isPermitted` be on the PathRule instead?

  def usesTotp: Boolean = totpSecret.isDefined
  def usesSecondFactor: Boolean = usesTotp || duoEnabled
  def doesNotUseSecondFactor: Boolean = !usesSecondFactor
  def isPermitted(rule: PathRule): Boolean = admin || rule.public || rule.permittedRoles.intersect(roles).nonEmpty
  def passwordCorrect(guess: String): Boolean = HashValidator.validateHash(passwordHash, guess)

  def totpCorrect(guess: String, leniency: Int = 1): Boolean = totpSecret match {
    case None         => Logger.warn(s"TOTP verification attempted on user $username without a known secret"); false
    case Some(secret) => TotpUtil.validateOneTimePassword(secret, guess, leniency)
  }
}