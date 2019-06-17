package services.rules

import java.net.URI

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import services.decoding.RequestInfo

@Singleton
class PathMatcher @Inject() (config: TraefikCopConfig) {

  private val Logger = play.api.Logger(this.getClass)

  def getRule(protocol: String, host: String, path: String): Option[PathRule] = {
    Logger.debug(s"Checking against protocol = `$protocol` & host = `$host` & path = `$path`")
    config.getPathRules.find(_.matches(protocol, host, path))
  }

  def getRule(requestInfo: RequestInfo): Option[PathRule] = getRule(requestInfo.protocol, requestInfo.host, requestInfo.path)
  def getRule(uri: URI): Option[PathRule] = getRule(uri.getScheme, uri.getHost, uri.getPath)
}
