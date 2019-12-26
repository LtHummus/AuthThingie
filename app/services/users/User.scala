package services.users

import java.util

import com.typesafe.config.Config
import play.api.ConfigLoader
import services.rules.PathRule
import services.totp.TotpUtil
import services.validator.HashValidator

import scala.jdk.CollectionConverters._

object User {
  implicit val configLoader = new ConfigLoader[List[User]] {
    override def load(config: Config, path: String): List[User] = {
      config.getObjectList(path).asScala.map { curr =>
        val unwrapped = curr.unwrapped().asScala

        val passwdLine = unwrapped("htpasswdLine").asInstanceOf[String]
        val admin = unwrapped.get("admin").exists(x => x.asInstanceOf[Boolean])
        val totpSecret = unwrapped.get("totpSecret").map(_.asInstanceOf[String])
        val roles = unwrapped.get("roles") match {
          case Some(roleList) => roleList.asInstanceOf[util.ArrayList[String]].asScala.toList
          case None           => List.empty[String]
        }

        User(passwdLine, admin, totpSecret, roles)
      }.toList
    }
  }
}

case class User(htpasswdLine: String, admin: Boolean, totpSecret: Option[String], roles: List[String]) {

  private val Logger = play.api.Logger(this.getClass)

  val (username, passwordHash) = getCredentialParts

  private def getCredentialParts: (String, String) = {
    val parts = htpasswdLine.split(":", 2)
    require(parts.length == 2, "Invalid credentials in config")

    (parts(0), parts(1))
  }

  //note for later: should `isPermitted` be on the PathRule instead?

  def usesTotp: Boolean = totpSecret.isDefined
  def doesNotUseTotp: Boolean = !usesTotp
  def isPermitted(rule: PathRule): Boolean = admin || rule.public || rule.permittedRoles.intersect(roles).nonEmpty
  def passwordCorrect(guess: String): Boolean = HashValidator.validateHash(passwordHash, guess)

  def totpCorrect(guess: String, leniency: Int = 1): Boolean = totpSecret match {
    case None         => Logger.warn(s"TOTP verification attempted on user $username without a known secret"); false
    case Some(secret) => TotpUtil.validateOneTimePassword(secret, guess, leniency)
  }
}