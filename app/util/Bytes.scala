package util

import org.apache.commons.codec.binary.Hex

import java.security.SecureRandom
import java.util.Base64

class Bytes private(bytes: Array[Byte]) {
  def byteArray: Array[Byte] = Array.copyOf(bytes, bytes.length)
  def asBase64: String = Base64.getEncoder.encodeToString(bytes)
  def asUrlBase64: String = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  def asHexString: String = Hex.encodeHexString(bytes)
}

object Bytes {
  private val Rng = new SecureRandom()


  def fromByteArray(x: Array[Byte]): Bytes = new Bytes(x)
  def fromBase64(x: String): Bytes = new Bytes(Base64.getDecoder.decode(x))
  def fromUrlBase64(x: String): Bytes = new Bytes(Base64.getUrlDecoder.decode(x))
  def fromHexString(x: String): Bytes = new Bytes(Hex.decodeHex(x))

  def cryptoRandom(n: Int): Bytes = {
    val bytes = Array.ofDim[Byte](n)
    Rng.nextBytes(bytes)
    new Bytes(bytes)
  }
}
