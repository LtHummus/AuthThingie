package util

import services.totp.{QrUtil, TotpUtil}

import scala.concurrent.duration._

case class GeneratorConfig(username: String = "", forcedSecret: Option[String] = None, qrMargin: Int = 1)

object GenerateTotp  {
  private val Bold = "\u001b[0;1m"
  private val Reset = "\u001b[0;0m"

  private def run(config: GeneratorConfig): Unit = {
    println("AuthThingie TOTP Generator")
    println(s"Generating for username $Bold${config.username}$Reset")
    println()

    val secret = config.forcedSecret.getOrElse(TotpUtil.generateSecret())

    val url = TotpUtil.totpUrl(config.username, "AuthThingie", secret)

    val qrCodeMatrix = QrUtil.generateQrCodeMatrix(url, config.qrMargin)
    QrUtil.printBitMatrix(qrCodeMatrix)

    val startingInstant = System.currentTimeMillis()
    val ThirtySeconds = 30.seconds.toMillis
    val codes = (0 until 5).map(x => TotpUtil.genOneTimePassword(secret, startingInstant + (ThirtySeconds * x))).mkString(", ")

    println()
    println("Step 1: Scan the QR code above to your OTP app of choice")
    println(s"Step 2: Validate you are generating the correct codes. Here are the next 5 codes to be generated: $codes")
    println(s"""Step 3: Add the following to the user value in your AuthThingie config for the user ${config.username}: "${Bold}totpSecret":  "$secret",$Reset""")
  }

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[GeneratorConfig]("auth_thingie_initializer") {
      head("auth_thingie_totp_initializer", "0.0.4")

      opt[String]("force-secret")
        .action((x, c) => c.copy(forcedSecret = Some(x)))
        .text("Force TOTP secret. Only use if you know what you're doing!")

      opt[Int]("qr-margin")
        .hidden()
        .action((x, c) => c.copy(qrMargin = x))
        .text("Border to be drawn for QR code")

      arg[String]("username")
        .required()
        .action((x, c) => c.copy(username = x))
        .text("Username to generate code for")
    }

    parser.parse(args, GeneratorConfig()) match {
      case None => System.exit(1)
      case Some(config) => run(config)
    }
  }


}
