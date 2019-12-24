package modules

import java.io.File

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import play.api.{ApplicationLoader, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class AuthThingieLoader extends GuiceApplicationLoader() {
  private val Logger = LoggerFactory.getLogger(getClass)

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val additionalConfig = sys.env.get("AUTHTHINGIE_CONFIG_FILE_PATH") match {
      case Some(filePath) =>
        Logger.debug(s"Loading fallback config from $filePath")
        ConfigFactory.parseFile(new File(filePath))
      case None => throw new IllegalStateException(s"AUTHTHINGIE_CONFIG_FILE_PATH is not set")
    }

    val combinedConfiguration = context.initialConfiguration.withFallback(Configuration(additionalConfig))

    initialBuilder
      .in(context.environment)
      .loadConfig(combinedConfiguration)
      .overrides(overrides(context): _*)

  }
}
