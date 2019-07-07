package services.totp

import java.nio.ByteBuffer
import java.security.SecureRandom

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base32

object TotpUtil {

  private val TimeStepMillis = 30000
  private val Algorithm = "HmacSHA1"
  private val Modulus = 1000000
  private val PasswordLength = 6
  private val KeyBytes = 160 / 8

  private def calculateHash(message: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val hmac = Mac.getInstance(Algorithm)
    val secretKey = new SecretKeySpec(key, "RAW")

    hmac.init(secretKey)
    hmac.doFinal(message)
  }

  private def secretToBytes(secret: String): Array[Byte] = new Base32().decode(secret)

  def generateSecret(): String = {
    val random = new SecureRandom()
    val bytes: Array[Byte] = Array.fill(KeyBytes)(0.toByte)
    random.nextBytes(bytes)
    new Base32().encodeToString(bytes)
  }

  def totpUrl(account: String, issuer: String, secret: String): String = s"otpauth://totp/$issuer:$account?secret=$secret&issuer=$issuer"

  def genOneTimePassword(secret: String, time: Long): String = {
    val timeCounter = time / TimeStepMillis
    val message = ByteBuffer.allocate(8).putLong(timeCounter).array()
    val hash = calculateHash(message, secretToBytes(secret))

    val offset: Int = hash(hash.length - 1) & 0x0F
    val value = ((hash(offset) & 0x7F) << 24) |
      ((hash(offset + 1) & 0xFF) << 16) |
      ((hash(offset + 2) & 0xFF) << 8) |
      (hash(offset + 0x03) & 0xFF)

    val otp = value % Modulus

    (("0" * PasswordLength) + otp.toString).takeRight(PasswordLength)
  }

  def validateOneTimePassword(secret: String, guess: String, leniency: Int = 1, startInstant: Long = System.currentTimeMillis()): Boolean = {
    val validInstants = (-leniency to leniency).map(x => startInstant + (x * TimeStepMillis))
    val validOtps = validInstants.map(genOneTimePassword(secret, _)).toSet

    validOtps.contains(guess)
  }
}
