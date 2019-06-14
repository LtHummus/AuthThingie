package services.validator

import javax.inject.{Inject, Singleton}

@Singleton
class HashValidator @Inject() () {
  def validateHash(hash: String, guess: String): Boolean = {
    HashAlgorithm.getHashAlgorithm(hash).guessCorrect(guess)
  }
}
