package services.totp

import java.util

import com.google.zxing.{BarcodeFormat, EncodeHintType}
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {
  private val BlackSquare = "\u001b[40m  \u001b[0m"
  private val WhiteSquare = "\u001b[47m  \u001b[0m"

  def generateQrCodeMatrix(contents: String, marginSize: Int = 1): BitMatrix = {
    val hints = new util.EnumMap[EncodeHintType, Any](classOf[EncodeHintType])
    hints.put(EncodeHintType.MARGIN, marginSize)

    val writer = new QRCodeWriter
    writer.encode(contents, BarcodeFormat.QR_CODE, 25, 25, hints)
  }

  def printBitMatrix(matrix: BitMatrix): Unit = {
    for (y <- 0 until matrix.getHeight) {
      for (x <- 0 until matrix.getWidth) {
        print(if (matrix.get(x, y)) BlackSquare else WhiteSquare)
      }
      println()
    }
  }
}
