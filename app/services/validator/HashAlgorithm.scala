package services.validator

import java.security.MessageDigest

import at.favre.lib.crypto.bcrypt.BCrypt
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.{DigestUtils, Md5Crypt}

private[validator] object HashAlgorithm {
  def getHashAlgorithm(hash: String): HashAlgorithm = {
    if (hash.startsWith("$apr1$")) {
      Apr1Hash(hash)
    } else if (hash.startsWith("{SHA}")) {
      Sha1Hash(hash)
    } else if (hash.startsWith("$2y$") || hash.startsWith("$2a")) { //there are technically other versions of bcrypt...are they used?
      BcryptHash(hash)
    } else {
      PlainTextPassword(hash)
    }
  }
}

private[validator] sealed trait HashAlgorithm {
  def guessCorrect(guess: String): Boolean
}

private[validator] case class Sha1Hash(hash: String) extends HashAlgorithm {
  private val Sha1PrefixLength = "{SHA}".length

  override def guessCorrect(guess: String): Boolean = {
    //SHA1 hash is in the following format: {SHA}<base 64 SHA1 has of the input>
    val guessHash = DigestUtils.sha1(guess)
    val correctHash = Base64.decodeBase64(hash.drop(Sha1PrefixLength))

    MessageDigest.isEqual(guessHash, correctHash)
  }
}

private[validator] case class Apr1Hash(hash: String) extends HashAlgorithm {
  private val Apr1HashRegex = "\\$apr1\\$([^\\$]+)\\$.*".r

  override def guessCorrect(guess: String): Boolean = {
    hash match {
      case Apr1HashRegex(salt) =>
        val calculatedHash = Md5Crypt.apr1Crypt(guess, salt)
        MessageDigest.isEqual(calculatedHash.getBytes, hash.getBytes)
      case _ => throw new Exception("Invalid hash in config file")
    }
  }
}

private[validator] case class BcryptHash(hash: String) extends HashAlgorithm {
  override def guessCorrect(guess: String): Boolean = {
    BCrypt.verifyer().verify(guess.toCharArray, hash).verified
  }
}

private[validator] case class PlainTextPassword(correct: String) extends HashAlgorithm {
  override def guessCorrect(guess: String): Boolean = MessageDigest.isEqual(guess.getBytes, correct.getBytes)
}