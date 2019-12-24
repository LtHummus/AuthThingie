package modules

import java.io.File

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import play.api.{ApplicationLoader, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class AuthThingieLoader extends GuiceApplicationLoader() {
  private val Logger = LoggerFactory.getLogger(getClass)
  private val MinSecretKeyLength = 16
  private val SecretKeyConfigPath = "play.http.secret.key"

  private def secretKeyIsBad(key: String): Boolean = key.length < MinSecretKeyLength || key.equalsIgnoreCase("SAMPLE_SECRET_KEY")

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val additionalConfig = sys.env.get("AUTHTHINGIE_CONFIG_FILE_PATH") match {
      case Some(filePath) =>
        Logger.debug(s"Loading fallback config from $filePath")
        ConfigFactory.parseFile(new File(filePath))
      case None => throw new IllegalStateException(s"AUTHTHINGIE_CONFIG_FILE_PATH is not set")
    }

    val combinedConfiguration = Configuration(additionalConfig).withFallback(context.initialConfiguration)
    val configPatch = if (secretKeyIsBad(combinedConfiguration.get[String](SecretKeyConfigPath))) {
      Logger.warn(s"Hey! Listen! Your secret key is crap! It should be at least 16 characters long and not the default that I ship with. " +
        s"The fundamental security of the app relies on it. I've randomly generated one for you for now, but this means that sessions will be invalidated " +
        s"when you restart the app. You should set (a strong) one manually by setting the config key `play.http.secret.key` in your " +
        s"config file or by setting the `AUTHTHINGIE_SECRET_KEY` environment variable.")

      val generatedSecretKey = RandomStringUtils.randomAlphanumeric(MinSecretKeyLength, MinSecretKeyLength * 2)
      Configuration(SecretKeyConfigPath -> generatedSecretKey)
    } else {
      Configuration()
    }

    initialBuilder
      .in(context.environment)
      .loadConfig(configPatch.withFallback(combinedConfiguration))
      .overrides(overrides(context): _*)


  }
}