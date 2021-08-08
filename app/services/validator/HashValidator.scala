package services.validator

import at.favre.lib.crypto.bcrypt.BCrypt

object HashValidator {
  val DefaultBcryptCost = 10

  def validateHash(hash: String, guess: String): Boolean = {
    HashAlgorithm.getHashAlgorithm(hash).guessCorrect(guess)
  }

  def hashPassword(password: String): String = BCrypt.withDefaults().hashToString(DefaultBcryptCost, password.toCharArray)
}
