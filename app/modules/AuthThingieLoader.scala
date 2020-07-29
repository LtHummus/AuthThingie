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
  private val PlayDomainConfigPath = "play.http.session.domain"
  private val PlaySessionExpirationPath = "play.http.session.maxAge"
  private val PlayJwtExpirationPath = "play.http.session.jwt.expiresAfter"

  private def secretKeyIsBad(key: String): Boolean = key.length < MinSecretKeyLength || key.equalsIgnoreCase("SAMPLE_SECRET_KEY")

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    Logger.info("Starting config file parsing")

    val additionalConfig = sys.env.get("AUTHTHINGIE_CONFIG_FILE_PATH") match {
      case Some(filePath) =>
        Logger.debug(s"Loading fallback config from $filePath")
        ConfigFactory.parseFile(new File(filePath))
      case None => throw new IllegalStateException(s"AUTHTHINGIE_CONFIG_FILE_PATH is not set")
    }

    val combinedConfiguration = Configuration(additionalConfig).withFallback(context.initialConfiguration)

    val authThingieTimeout = combinedConfiguration.getOptional[String]("auththingie.timeout").map { timeout =>
      Seq(PlaySessionExpirationPath -> timeout, PlayJwtExpirationPath -> timeout)
    }.getOrElse(Seq())

    val domain = combinedConfiguration.getOptional[String]("auththingie.domain").map { domain =>
      Seq(PlayDomainConfigPath -> domain)
    }.getOrElse {
      if (!combinedConfiguration.has(PlayDomainConfigPath))
        Logger.warn("No configuration found for `auththingie.domain`. This may affect cookie persistence between subdomains. " +
        "If you are having issues, set the `auththingie.domain` config value to your domain name. For example, if your services " +
        "are hosted at foo.example.com, bar.example.com, etc, add `auththingie.domain: example.com` to your config file. ")
      Seq()
    }

    val authThingieSecretKey = combinedConfiguration.getOptional[String](SecretKeyConfigPath)
    val secretKeyPatch = if (authThingieSecretKey.isEmpty || authThingieSecretKey.exists(x => secretKeyIsBad(x))) {
      Logger.warn("Hey! Listen! Your secret key is crap! It should be at least 16 characters long and not the default that I ship with. " +
        "The fundamental security of the app relies on it. I've randomly generated one for you for now, but this means that sessions will be invalidated " +
        "when you restart the app. You should set (a strong) one manually by setting the `AUTHTHINGIE_SECRET_KEY` environment variable (recommended) " +
        "or by setting the config key `play.http.secret.key` in your config file")

      val generatedSecretKey = RandomStringUtils.randomAlphanumeric(MinSecretKeyLength, MinSecretKeyLength * 2)
      Seq((SecretKeyConfigPath -> generatedSecretKey))
    } else {
      Seq()
    }

    val configPatch = Configuration((domain ++ authThingieTimeout ++ secretKeyPatch): _*)

    initialBuilder
      .in(context.environment)
      .loadConfig(configPatch.withFallback(combinedConfiguration))
      .overrides(overrides(context): _*)


  }
}
