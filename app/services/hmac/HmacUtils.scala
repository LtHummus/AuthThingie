package services.hmac

import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.annotation.tailrec

object HmacUtils {

  private val SigningKey = {
    val sr = new SecureRandom()
    val keyBytes = Array.ofDim[Byte](32)
    sr.nextBytes(keyBytes)

    new SecretKeySpec(keyBytes, "RAW")
  }

  private def timeSafeStringEquals(a: String, b: String): Boolean = {
    @tailrec def internal(currA: String, currB: String, currRes: Boolean): Boolean = {
      require(currA.length == currB.length)
      if (currA.isEmpty && currB.isEmpty) {
        currRes
      } else {
        val currComparison = currA.head == currB.head
        internal(currA.tail, currB.tail, currRes & currComparison)
      }
    }

    if (a.length != b.length) {
      false
    } else {
      internal(a, b, currRes = true)
    }
  }

  def sign(payload: String): String = {
    val mac = Mac.getInstance("HmacSHA384")
    mac.init(SigningKey)

    val digest = mac.doFinal(payload.getBytes)

    Base64.getUrlEncoder.encodeToString(digest)
  }

  def validate(payload: String, givenSignature: String): Boolean = {
    val computedSignature = sign(payload)

    timeSafeStringEquals(computedSignature, givenSignature)
  }

}
