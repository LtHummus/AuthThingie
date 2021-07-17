package services.storage

import anorm._
import play.api.db.Database
import util.{Bytes, DatabaseExecutionContext}

import javax.inject.{Inject, Singleton}

@Singleton
class SqlStorageService @Inject() (db: Database, dec: DatabaseExecutionContext) {
  def getCredentialIdsForUsername(username: String): Set[Bytes] = {
    db.withConnection { implicit c =>
      SQL"SELECT keyId FROM keys JOIN users ON keys.user = users.id WHERE username = $username"
        .as(SqlParser.str("keyId").map{ x =>
          Bytes.fromUrlBase64(x)
        }.*).toSet
    }
  }
}
