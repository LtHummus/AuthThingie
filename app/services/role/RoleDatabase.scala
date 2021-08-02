package services.role

import anorm.SqlParser.str
import anorm.SqlStringInterpolation
import play.api.db.Database

import javax.inject.{Inject, Singleton}

@Singleton
class RoleDatabase @Inject() (db: Database) {

  def createRole(role: String) = {
    db.withConnection { implicit c =>
      SQL"INSERT INTO roles (role) VALUES ($role)".executeInsert()
    }
  }

  def listRoles(): List[String] = {
    db.withConnection { implicit c =>
      SQL"SELECT role FROM roles".as(str("role").*)
    }
  }

  def attachRoleToUser(username: String, role: String) = {
    db.withConnection { implicit c =>
      SQL"""
           INSERT INTO users_x_role (user, role)
           VALUES ((SELECT id FROM users WHERE username = $username), (SELECT id FROM roles WHERE role = $role))""".executeInsert()
    }
  }

  def attachRoleToRule(ruleName: String, role: String) = {
    db.withConnection { implicit c =>
      SQL"""
            INSERT INTO rules_x_role (rule, role)
            VALUES ((SELECT id FROM rules WHERE name = $ruleName), (SELECT id FROM roles WHERE role = $role))
          """.executeInsert()
    }
  }
}
