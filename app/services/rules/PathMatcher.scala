package services.rules

import java.net.URI
import config.AuthThingieConfig

import javax.inject.{Inject, Singleton}
import services.decoding.RequestInfo
import services.storage.SqlStorageService

@Singleton
class PathMatcher @Inject() (storage: SqlStorageService) {

  private val Logger = play.api.Logger(this.getClass)

  def allRules(): List[PathRule] = storage.getRules()

  def getRule(protocol: String, host: String, path: String): Option[PathRule] = {
    Logger.debug(s"Checking against protocol = `$protocol` & host = `$host` & path = `$path`")
    allRules().find(_.matches(protocol, host, path))
  }

  def getRule(requestInfo: RequestInfo): Option[PathRule] = getRule(requestInfo.protocol, requestInfo.host, requestInfo.path)
  def getRule(uri: URI): Option[PathRule] = getRule(uri.getScheme, uri.getHost, uri.getPath)
}
