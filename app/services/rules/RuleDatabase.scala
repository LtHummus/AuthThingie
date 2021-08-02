package services.rules

import anorm.{SQL, SqlStringInterpolation, ~}
import anorm.SqlParser.{get, int, str}
import play.api.db.Database

import java.time.Duration
import javax.inject.{Inject, Singleton}

@Singleton
class RuleDatabase @Inject() (db: Database) {
  private val ruleObjectParser = (str("name") ~ get[Option[String]]("protocol_pattern") ~ get[Option[String]]("host_pattern") ~ get[Option[String]]("path_pattern") ~ int("is_public") ~ get[Option[Long]]("timeout") ~ get[Option[String]]("roles")).map {
    case name ~ protocolPattern ~ hostPattern ~ pathPattern ~ isPublic ~ timeout ~ roles =>
      val roleList = roles.map(r => r.split(31.toChar).toList).getOrElse(List())
      val duration = timeout.map(Duration.ofSeconds)
      PathRule(name, protocolPattern, hostPattern, pathPattern, isPublic == 1, roleList, duration)
  }

  def getRules(): List[PathRule] = {
    db.withConnection { implicit c =>
      SQL"""SELECT name, protocol_pattern, host_pattern, path_pattern, is_public, timeout, GROUP_CONCAT(r.role, CHAR(31)) AS roles
           FROM rules
                    LEFT JOIN rules_x_role uxr on rules.id = uxr.rule
                    LEFT JOIN roles r on uxr.role = r.id
           GROUP BY rules.id
           """.as(ruleObjectParser.*)
    }
  }

  def createRule(rule: PathRule) = {
    db.withConnection { implicit c =>
      SQL"""INSERT INTO rules (name, protocol_pattern, host_pattern, path_pattern, is_public, timeout) VALUES
          (${rule.name}, ${rule.protocolPattern}, ${rule.hostPattern}, ${rule.pathPattern}, ${rule.public}, ${rule.timeout.map(_.toSeconds)})
         """.executeInsert()
    }
  }
}
