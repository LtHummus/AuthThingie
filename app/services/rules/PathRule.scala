package services.rules

import java.time.Duration
import java.util

import com.typesafe.config.Config
import org.apache.commons.io.{FilenameUtils, IOCase}
import play.api.ConfigLoader

import scala.jdk.CollectionConverters._
import scala.util.Try

object PathRule {
  private val Logger = play.api.Logger(this.getClass)


  implicit val configLoader = new ConfigLoader[List[PathRule]] {
    override def load(config: Config, path: String): List[PathRule] = {
      config.getConfigList(path).asScala.map { curr =>
        val name = curr.getString("name")
        val protocolPattern = Try(curr.getString("protocolPattern")).toOption
        val hostPattern = Try(curr.getString("hostPattern")).toOption
        val pathPattern = Try(curr.getString("pathPattern")).toOption
        val isPublic = Try(curr.getBoolean("public")).getOrElse(false)
        val permittedRoles = Try(curr.getStringList("permittedRoles").asScala.toSeq).getOrElse(Seq.empty[String])
        val timeout = Try(curr.getDuration("timeout")).toOption

        Logger.info(s"Parsed $timeout for $name")

        PathRule(name, protocolPattern, hostPattern, pathPattern, isPublic, permittedRoles, timeout)
      }.toList
    }
  }
}

case class PathRule(name: String, protocolPattern: Option[String], hostPattern: Option[String], pathPattern: Option[String], public: Boolean, permittedRoles: Seq[String], timeout: Option[Duration] = None) {
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