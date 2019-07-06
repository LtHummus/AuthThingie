package services.users

import services.rules.PathRule
import services.totp.TotpUtil
import services.validator.HashValidator

case class User(htpasswdLine: String, admin: Boolean, totpSecret: Option[String], roles: List[String]) {

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
    case None         => require(requirement = false, s"TOTP verification attempted on user $username without one"); false
    case Some(secret) => TotpUtil.validateOneTimePassword(secret, guess, leniency)
  }
}