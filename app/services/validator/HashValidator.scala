package services.validator

object HashValidator {
  def validateHash(hash: String, guess: String): Boolean = {
    HashAlgorithm.getHashAlgorithm(hash).guessCorrect(guess)
  }
}
