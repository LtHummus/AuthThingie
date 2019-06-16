package services.rules

import scala.util.matching.Regex

case class PathRule(name: String, protocol: Option[String], host: Option[String], path: Option[String], public: Boolean, permittedRoles: List[String]) {
  private val protocolRegex = protocol.map(_.r)
  private val hostRegex = host.map(_.r)
  private val pathRegex = path.map(_.r)

  private def matches(subject: String, goal: Option[Regex]): Boolean = {
    goal match {
      case None        => true //nothing specified always matches
      case Some(regex) => regex.pattern.matcher(subject).matches()
    }
  }

  def matches(protocol: String, host: String, path: String): Boolean = {
    matches(protocol, protocolRegex) && matches(host, hostRegex) && matches(path, pathRegex)
  }
}