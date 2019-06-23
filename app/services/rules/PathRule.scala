package services.rules

import org.apache.commons.io.{FilenameUtils, IOCase}

object PathRule {
  private val Logger = play.api.Logger(this.getClass)
}

case class PathRule(name: String, protocolPattern: Option[String], hostPattern: Option[String], pathPattern: Option[String], public: Boolean, permittedRoles: List[String]) {
  import PathRule._

  private def matches(subject: String, pattern: Option[String]): Boolean = {
    pattern match {
      case None                => Logger.debug(s"[$name] $subject matches because no pattern"); true //nothing specified always matches
      case Some(actualPattern) => Logger.debug(s"[$name] checking if $subject matches $actualPattern"); FilenameUtils.wildcardMatch(subject, actualPattern, IOCase.INSENSITIVE)
    }
  }

  def matches(protocol: String, host: String, path: String): Boolean = {
    Logger.debug(s"[$name] Checking rule")
    matches(protocol, protocolPattern) && matches(host, hostPattern) && matches(path, pathPattern)
  }
}