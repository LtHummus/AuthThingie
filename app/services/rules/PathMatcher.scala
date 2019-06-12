package services.rules

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import services.decoding.RequestInfo

@Singleton
class PathMatcher @Inject() (config: TraefikCopConfig) {

  private val Logger = play.api.Logger(this.getClass)

  def getRule(protocol: String, host: String, path: String): Option[PathRule] = {
    Logger.debug(s"Checking against protocol = `$protocol` & server = `$host` & path = `$path`")
    config.getPathRules.find(_.matches(protocol, host, path))
  }

  def getRule(requestInfo: RequestInfo): Option[PathRule] = getRule(requestInfo.protocol, requestInfo.host, requestInfo.path)

}
