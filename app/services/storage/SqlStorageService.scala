package services.storage

import anorm._
import anorm.SqlParser.{bool, flatten, get, int, long, str}
import play.api.db.Database
import services.rules.PathRule
import services.users.User
import services.webauthn.SavedKey
import util.{Bytes, DatabaseExecutionContext}

import java.time.Duration
import javax.inject.{Inject, Singleton}

@Singleton
class SqlStorageService @Inject() (db: Database, dec: DatabaseExecutionContext) {
  def getCredentialIdsForUsername(username: String): Set[Bytes] = {
    db.withConnection { implicit c =>
      SQL"SELECT keyId FROM keys JOIN users ON keys.user = users.id WHERE username = $username"
        .as(SqlParser.str("keyId").map{ x =>
          Bytes.fromBase64(x)
        }.*).toSet
    }
  }

  def setupNeeded(): Boolean = {
    db.withConnection { implicit c =>
      SQL"SELECT COUNT(*) AS userCount FROM users".as(int("userCount").single) == 0
    }
  }

  def createUser(username: String, passwordHash: String, isAdmin: Boolean) = {
    val generatedHandle = Bytes.cryptoRandom(16).asBase64
    db.withConnection { implicit c =>
      SQL"INSERT INTO users (username, password, handle, isAdmin) VALUES ($username, $passwordHash, $generatedHandle, $isAdmin)".executeInsert()
    }
  }

  private val userObjectRowParser = (str("username") ~ str("password") ~ str("handle") ~ int("isAdmin") ~ int("duo_enabled") ~ get[Option[String]]("totp_secret") ~ get[Option[String]]("roles")).map {
    case username ~ password ~ handle ~ isAdmin ~ duoEnabled ~ totpSecret ~ roles =>
      val roleList = roles.map(l => l.split('|').toList).getOrElse(List())
      User(s"${username}:${password}", Bytes.fromBase64(handle), isAdmin == 1, totpSecret, roleList, duoEnabled == 1) // TODO: replace with actual values
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

  def deleteCredentialForUsername(username: String, keyId: String): Int = {
    db.withConnection { implicit c =>
      SQL"DELETE FROM keys WHERE ROWID IN (SELECT keys.ROWID FROM keys JOIN users ON users.id = keys.user WHERE keyId = $keyId AND users.username = $username)".executeUpdate()
    }
  }

  private val userRowParser = (int("id") ~ str("username") ~ str("handle")).map {
    case id ~ username ~ handle => UserEntry(id, username, Bytes.fromBase64(handle))
  }

  def getUserByUsername(username: String): Option[UserEntry] = {
    db.withConnection { implicit c =>
      SQL"SELECT id, username, handle FROM users WHERE username = $username"
        .as(userRowParser.singleOpt)
    }
  }

  def persistKey(userId: Int, key: SavedKey) = {
    db.withConnection { implicit c =>
      SQL"INSERT INTO keys(user, keyId, credentialData, statement, counter) VALUES ($userId, ${key.keyId.asBase64}, ${key.attestedCredentialData.asBase64}, ${key.statement.asBase64}, ${key.counter})"
        .executeInsert()
    }
  }

  private val keyParser = (str("keyId") ~ str("credentialData") ~ str("statement") ~ long("counter")).map {
    case id ~ data ~ statement ~ ctr =>
      SavedKey(Bytes.fromBase64(id),
        Bytes.fromBase64(data),
        Bytes.fromBase64(statement),
        ctr)
  }

  def getKeyByIdAndUser(user: User, key: Array[Byte]): Option[SavedKey] = {
    val keyIdStr = Bytes.fromByteArray(key).asBase64
    db.withConnection { implicit c =>
      SQL"SELECT keyId, credentialData, statement, counter FROM keys JOIN users ON keys.user = users.id WHERE keyId = $keyIdStr AND username = ${user.username}"
        .as(keyParser.singleOpt)
    }
  }

  def getKeyById(key: Array[Byte]): Option[SavedKey] = {
    val keyIdStr = Bytes.fromByteArray(key).asBase64
    db.withConnection { implicit c =>
      SQL"SELECT keyId, credentialData, statement, counter FROM keys WHERE keyId = $keyIdStr"
        .as(keyParser.singleOpt)
    }
  }

  def findKeyByPotentialUserAndId(user: Option[User], key: Array[Byte]): Option[SavedKey] = {
    user match {
      case None    => getKeyById(key)
      case Some(u) => getKeyByIdAndUser(u, key)
    }
  }

  def updateSignCounter(key: Array[Byte], signCount: Long): Int = {
    val keyId = Bytes.fromByteArray(key).asBase64
    db.withConnection { implicit c =>
      SQL"UPDATE keys SET counter = $signCount WHERE keyId = $keyId".executeUpdate()
    }
  }

  def getUsernameForKeyId(key: Array[Byte]): Option[String] = {
    db.withConnection { implicit c =>
      SQL"SELECT username FROM users JOIN keys ON users.id = keys.user WHERE keyId = ${Bytes.fromByteArray(key).asBase64}"
        .as(str("username").singleOpt)
    }
  }

  private val ruleObjectParser = (str("name") ~ get[Option[String]]("protocol_pattern") ~ get[Option[String]]("host_pattern") ~ get[Option[String]]("path_pattern") ~ int("is_public") ~ get[Option[Long]]("timeout") ~ get[Option[String]]("roles")).map {
    case name ~ protocolPattern ~ hostPattern ~ pathPattern ~ isPublic ~ timeout ~ roles =>
      val roleList = roles.map(r => r.split('|').toList).getOrElse(List())
      val duration = timeout.map(Duration.ofSeconds)
      PathRule(name, protocolPattern, hostPattern, pathPattern, isPublic == 1, roleList, duration)
  }

  def getRules(): List[PathRule] = {
    db.withConnection { implicit c =>
      SQL"""SELECT name, protocol_pattern, host_pattern, path_pattern, is_public, timeout, GROUP_CONCAT(r.role, '|') AS roles
           FROM rules
                    LEFT JOIN rules_x_role uxr on rules.id = uxr.rule
                    LEFT JOIN roles r on uxr.role = r.id
           GROUP BY rules.id
           """.as(ruleObjectParser.*)
    }
  }

}
