package services.users

import anorm.{SQL, SqlStringInterpolation, ~}
import anorm.SqlParser.{get, int, str}
import play.api.db.Database
import util.Bytes

import javax.inject.{Inject, Singleton}

@Singleton
class UserDatabase @Inject() (db: Database) {
  private val userObjectRowParser = (str("username") ~ str("password") ~ str("handle") ~ int("isAdmin") ~ int("duo_enabled") ~ get[Option[String]]("totp_secret") ~ get[Option[String]]("roles")).map {
    case username ~ password ~ handle ~ isAdmin ~ duoEnabled ~ totpSecret ~ roles =>
      val roleList = roles.map(l => l.split('|').toList).getOrElse(List())
      User(s"${username}:${password}", Bytes.fromBase64(handle), isAdmin == 1, totpSecret, roleList, duoEnabled == 1) // TODO: replace with actual values
  }

  def createUser(username: String, passwordHash: String, isAdmin: Boolean, duoEnabled: Boolean = false, totpSecret: Option[String] = None) = {
    val generatedHandle = Bytes.cryptoRandom(16).asBase64
    db.withConnection { implicit c =>
      SQL"INSERT INTO users (username, password, handle, isAdmin, duo_enabled, totp_secret) VALUES ($username, $passwordHash, $generatedHandle, $isAdmin, $duoEnabled, $totpSecret)".executeInsert()
    }
  }

  def getUser(username: String): Option[User] = {
    db.withConnection { implicit c =>
      SQL"""SELECT username, password, handle, isAdmin, duo_enabled, totp_secret, GROUP_CONCAT(r.role, '|') AS roles
                      FROM users
                               LEFT JOIN users_x_role uxr on users.id = uxr.user
                               LEFT JOIN roles r on uxr.role = r.id
                      WHERE username = 'ben'
                      GROUP BY users.id""".as(userObjectRowParser.singleOpt)
    }
  }

  def getAllUsers(): List[User] = {
    db.withConnection { implicit c =>
      SQL"""SELECT username, password, handle, isAdmin, duo_enabled, totp_secret, GROUP_CONCAT(r.role, '|') AS roles
            FROM users
              LEFT JOIN users_x_role uxr on users.id = uxr.user
              LEFT JOIN roles r on uxr.role = r.id
            GROUP BY users.id""".as(userObjectRowParser.*)
    }
  }

}
