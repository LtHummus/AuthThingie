package services.pathmatching

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}

@Singleton
class PathMatcher @Inject() (config: TraefikCopConfig) {

  private val Logger = play.api.Logger(this.getClass)

  val Rules: Seq[PathRule] = config.getPathRules

  def getRule(protocol: String, server: String, path: String): Option[PathRule] = {
    Logger.info(s"Checking against protocol = `$protocol` & server = `$server` & path = `$path`")
    Rules.find(_.matches(protocol, server, path))
  }

}
