package services.pathmatching

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}

@Singleton
class PathMatcher @Inject() (config: TraefikCopConfig) {

  private val Logger = play.api.Logger(this.getClass)

  val Rules: Seq[PathRule] = config.getPathRules

  def isPublic(protocol: String, server: String, path: String): Boolean = {
    Logger.info(s"Checking against protocol = `$protocol` & server = `$server` & path = `$path`")
    Rules.find(_.matches(protocol, server, path)) match {
      case None       => Logger.info("No path matched."); false //rule not found, non-public
      case Some(rule) => Logger.info(s"Found rule ${rule.name}"); rule.public //found a rule
    }
  }

}
