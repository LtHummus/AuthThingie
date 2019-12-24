package modules

import play.api.{ApplicationLoader, Configuration}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

class AuthThingieLoader extends GuiceApplicationLoader() {

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val additionalConfig = Configuration("foo" -> "bar")

    initialBuilder
      .in(context.environment)
      .loadConfig(context.initialConfiguration.withFallback(additionalConfig))
      .overrides(overrides(context): _*)

  }
}
