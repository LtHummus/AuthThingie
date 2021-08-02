package modules

import java.io.File

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import play.api.{ApplicationLoader, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class AuthThingieLoader extends GuiceApplicationLoader() {
  private val Logger = LoggerFactory.getLogger(this.getClass)
  private val MinSecretKeyLength = 16

  private val AuthThingieSecretKeyConfigPath = "auththingie.secretKey"
  private val AuthThingieTimeoutConfigPath = "auththingie.timeout"
  private val SecretKeyConfigPath = "play.http.secret.key"
  private val PlayDomainConfigPath = "play.http.session.domain"
  private val PlaySessionExpirationPath = "play.http.session.maxAge"
  private val PlayJwtExpirationPath = "play.http.session.jwt.expiresAfter"

  private val WebauthnDatabasePath = "auththingie.webauthn.database"

  private val PlayDatabasePath = "db.default.url"
  private val PlayDatabaseDriverPath = "db.default.driver"
  private val PlayDatabaseLogPath = "db.default.logSql"
  private val PlayDatabaseInitSqlPath = "db.default.initSQL"


  private val OneYear = "365d"
  private val OneDay = "1d"

  private val SecretKeyEnvVarName = "AUTHTHINGIE_SECRET_KEY"

  private def secretKeyIsBad(key: String): Boolean = key.length < MinSecretKeyLength || key.equalsIgnoreCase("SAMPLE_SECRET_KEY")

  private def loadSecretKey(config: Configuration): Option[String] = {
    // we can load the secret key from one of a number of places....so let's do it in priority order
    if (sys.env.contains(SecretKeyEnvVarName)) {
      Logger.warn(s"Loading secret key from environment variable $SecretKeyEnvVarName")
      Some(sys.env(SecretKeyEnvVarName))
    } else if (config.has(AuthThingieSecretKeyConfigPath)) {
      Logger.warn(s"Loading secret key from config path $AuthThingieSecretKeyConfigPath")
      Some(config.get[String](AuthThingieSecretKeyConfigPath))
    } else if (config.has(SecretKeyConfigPath)) {
      Logger.warn(s"Loading secret key from config path $SecretKeyConfigPath")
      Some(config.get[String](SecretKeyConfigPath))
    }  else {
      // no secret key has been specified anywhere
      None
    }
  }

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    Logger.info("Starting config file parsing")

    // this fallback exists because there's a bug in IntelliJ where environment variables that are set in a run configuration
    // for Play 2 are actually passed as Java System Properties. I have no idea why this is still a bug, but :shrug:
    //
    // https://youtrack.jetbrains.com/issue/SCL-11175
    val potentialFile = Seq(sys.env.get("AUTHTHINGIE_CONFIG_FILE_PATH"), sys.props.get("AUTHTHINGIE_CONFIG_FILE_PATH")).flatten.headOption

    val additionalConfig = potentialFile match {
      case Some(filePath) =>
        Logger.debug(s"Loading fallback config from $filePath")
        ConfigFactory.parseFile(new File(filePath))
      case None => throw new IllegalStateException(s"AUTHTHINGIE_CONFIG_FILE_PATH is not set")
    }

    val combinedConfiguration = Configuration(additionalConfig).withFallback(context.initialConfiguration)

    val authThingieTimeout = if (!combinedConfiguration.has(AuthThingieTimeoutConfigPath)) {
      Logger.warn(s"I've changed the way that I handle timeouts in 0.1.1. Please set the config key $AuthThingieTimeoutConfigPath with the timeout instead")
      if (!combinedConfiguration.has(PlaySessionExpirationPath)) {
        Seq(PlaySessionExpirationPath -> OneDay)
      } else {
        Seq()
      }
    } else {
      Seq(PlaySessionExpirationPath -> OneYear, PlayJwtExpirationPath -> OneYear)
    }

    val databasePatch = combinedConfiguration.getOptional[String](WebauthnDatabasePath) match {
      case None     => Seq()
      case Some(db) =>
        Logger.info(s"Using $db as database path")
        Seq(PlayDatabasePath -> s"jdbc:sqlite:$db", PlayDatabaseDriverPath -> "org.sqlite.JDBC", PlayDatabaseLogPath -> true, PlayDatabaseInitSqlPath -> "PRAGMA foreign_keys = true")
    }

    val domain = combinedConfiguration.getOptional[String]("auththingie.domain").map { domain =>
      Seq(PlayDomainConfigPath -> domain)
    }.getOrElse {
      if (!combinedConfiguration.has(PlayDomainConfigPath))
        Logger.warn("No configuration found for `auththingie.domain`. This may affect cookie persistence between subdomains. " +
        "If you are having issues, set the `auththingie.domain` config value to your domain name. For example, if your services " +
        "are hosted at foo.example.com, bar.example.com, etc, add `auththingie.domain: example.com` to your config file. ")
      Seq()
    }

    val authThingieSecretKey = loadSecretKey(combinedConfiguration)
    val secretKeyPatch = if (authThingieSecretKey.isEmpty || authThingieSecretKey.exists(x => secretKeyIsBad(x))) {
      Logger.warn("Hey! Listen! Your secret key is crap! It should be at least 16 characters long and not the default that I ship with. " +
        "The fundamental security of the app relies on it. I've randomly generated one for you for now, but this means that sessions will be invalidated " +
        "when you restart the app. You should set (a strong) one manually by setting the `AUTHTHINGIE_SECRET_KEY` environment variable (recommended) " +
        "or by setting the config key `play.http.secret.key` in your config file")

      val generatedSecretKey = RandomStringUtils.randomAlphanumeric(MinSecretKeyLength, MinSecretKeyLength * 2)
      Seq((SecretKeyConfigPath -> generatedSecretKey))
    } else {
      require(authThingieSecretKey.isDefined, "No secret key defined, yet we think one is!")
      Seq((SecretKeyConfigPath -> authThingieSecretKey.get))
    }

    val configPatch = Configuration((domain ++ authThingieTimeout ++ secretKeyPatch ++ databasePatch): _*)

    initialBuilder
      .in(context.environment)
      .loadConfig(configPatch.withFallback(combinedConfiguration))
      .overrides(overrides(context): _*)


  }
}
