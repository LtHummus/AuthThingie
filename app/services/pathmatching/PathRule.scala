package services.pathmatching

import scala.util.matching.Regex

case class PathRule(name: String, protocol: Option[String], server: Option[String], path: Option[String], public: Boolean) {
  private val protocolRegex = protocol.map(_.r)
  private val serverRegex = server.map(_.r)
  private val pathRegex = path.map(_.r)

  private def matches(subject: String, goal: Option[Regex]): Boolean = {
    goal match {
      case None        => true //nothing specified always matches
      case Some(regex) => regex.pattern.matcher(subject).matches()
    }
  }

  def matches(protocol: String, server: String, path: String): Boolean = {
    matches(protocol, protocolRegex) && matches(server, serverRegex) && matches(path, pathRegex)
  }
}