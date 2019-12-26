package services.rules

import java.util

import com.typesafe.config.Config
import org.apache.commons.io.{FilenameUtils, IOCase}
import play.api.ConfigLoader

import scala.collection.JavaConverters._

object PathRule {
  private val Logger = play.api.Logger(this.getClass)

  implicit val configLoader = new ConfigLoader[List[PathRule]] {
    override def load(config: Config, path: String): List[PathRule] = {
      config.getObjectList(path).asScala.map { curr =>
        val dataMap = curr.unwrapped().asScala
        val name = dataMap("name").asInstanceOf[String]
        val protocolPattern = dataMap.get("protocolPattern").map(_.asInstanceOf[String])
        val hostPattern = dataMap.get("hostPattern").map(_.asInstanceOf[String])
        val pathPattern = dataMap.get("pathPattern").map(_.asInstanceOf[String])
        val isPublic = dataMap.get("public").map(_.asInstanceOf[Boolean]).contains(true)
        val permittedRoles = dataMap.get("permittedRoles") match {
          case Some(roleList) => roleList.asInstanceOf[util.ArrayList[String]].asScala.toList
          case None           => List.empty[String]
        }

        PathRule(name, protocolPattern, hostPattern, pathPattern, isPublic, permittedRoles)
      }.toList
    }
  }
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