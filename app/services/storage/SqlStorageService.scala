package services.storage

import anorm._
import anorm.SqlParser.{flatten, int, long, str}
import play.api.db.Database
import services.webauthn.SavedKey
import util.{Bytes, DatabaseExecutionContext}

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

  def createOrGetUser(username: String): UserEntry = {
    val generatedHandle = Bytes.cryptoRandom(16).asBase64
    db.withConnection { implicit c =>
      SQL"INSERT OR IGNORE INTO users(username, handle) VALUES ($username, $generatedHandle)".executeInsert()
    }

    getUserByUsername(username) match {
      case Some(u) => u
      case None    => ???
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

  def getKeyById(key: Array[Byte]): Option[SavedKey] = {
    val keyIdStr = Bytes.fromByteArray(key).asBase64
    db.withConnection { implicit c =>
      SQL"SELECT keyId, credentialData, statement, counter FROM keys WHERE keyId = $keyIdStr"
        .as(keyParser.singleOpt)
    }
  }

  def updateSignCounter(key: Array[Byte], signCount: Long) = {
    val keyId = Bytes.fromByteArray(key).asBase64
    db.withConnection { implicit c =>
      SQL"UPDATE keys SET counter = $signCount WHERE keyId = $keyId".executeUpdate()
    }
  }


}
