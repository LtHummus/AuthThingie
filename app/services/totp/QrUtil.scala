package services.totp

import java.util

import com.google.zxing.{BarcodeFormat, EncodeHintType}
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.{Inject, Singleton}

//do we need to make this a class instead of an object and have Guice know about it? For now, the service doesn't know
//about it, and that's fine, only the generator app does...hmmm
@Singleton
class QrUtil @Inject() () {
  private val BlackSquare = "\033[40m  \033[0m"
  private val WhiteSquare = "\033[47m  \033[0m"

  def generateQrCodeMatrix(contents: String): BitMatrix = {
    val hints = new util.EnumMap[EncodeHintType, Any](classOf[EncodeHintType])
    hints.put(EncodeHintType.MARGIN, 2)

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
