package services.webauthn

import anorm.SqlParser.{flatten, int, long, str}
import com.yubico.webauthn.data.{ByteArray, PublicKeyCredentialDescriptor, UserIdentity}
import com.yubico.webauthn.{CredentialRepository, RegisteredCredential}
import play.api.db.Database
import util.DatabaseExecutionContext

import java.util
import java.util.Optional
import javax.inject.{Inject, Singleton}
import anorm._
import com.yubico.webauthn.attestation.Attestation

import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.OptionConverters.RichOption

case class CredentialRegistration(credential: RegisteredCredential, id: UserIdentity, nickname: Option[String], attestation: Option[Attestation])

@Singleton
class SqlCredentialRepo @Inject() (db: Database, dec: DatabaseExecutionContext) extends CredentialRepository {

  private val Logger = play.api.Logger(this.getClass)

  private val registeredCredentialParser = (str("keyId") ~ str("handle") ~ str("publicKey") ~ long("signatureCount")).map {
    case keyId ~ handle ~ publicKey ~ sigCount => RegisteredCredential.builder()
      .credentialId(ByteArray.fromHex(keyId))
      .userHandle(ByteArray.fromHex(handle))
      .publicKeyCose(ByteArray.fromHex(publicKey))
      .signatureCount(sigCount)
      .build()
  }

  override def getCredentialIdsForUsername(username: String): util.Set[PublicKeyCredentialDescriptor] = {
    db.withConnection{ implicit c =>
      SQL("SELECT keyId FROM keys JOIN users ON keys.user = users.id WHERE username = {username}")
        .on("username" -> username)
        .as(SqlParser.str("keyId").map(x => {
          PublicKeyCredentialDescriptor.builder()
            .id(ByteArray.fromHex(x))
            .build()
        }).*).toSet.asJava
    }
  }

  override def getUserHandleForUsername(username: String): Optional[ByteArray] = {
    db.withConnection { implicit c =>
      val result = SQL("SELECT handle FROM users WHERE username = {username}").on("username" -> username).as(SqlParser.str("handle").singleOpt)
      result.map(ByteArray.fromHex)
    }.toJava
  }

  override def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] = {
    db.withConnection { implicit c =>
      SQL("SELECT username FROM users WHERE handle = {handle}").on("handle" -> userHandle.getHex).as(SqlParser.str("username").singleOpt)
    }.toJava
  }

  override def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] = {
    db.withConnection{ implicit c =>
      SQL("SELECT keyId, handle, publicKey, signatureCount FROM keys JOIN users ON keys.user = users.id WHERE keyId = {keyId} AND handle = {handle}")
        .on("keyId" -> credentialId.getHex, "handle" -> userHandle.getHex)
        .as(registeredCredentialParser.singleOpt)
    }.toJava
  }

  override def lookupAll(credentialId: ByteArray): util.Set[RegisteredCredential] = {
    db.withConnection { implicit c =>
      SQL("SELECT keyId, handle, publicKey, signatureCount FROM keys JOIN users ON keys.user = users.id WHERE keyId = {keyId}")
        .on("keyId" -> credentialId.getHex)
        .as(registeredCredentialParser.*)
    }.toSet.asJava
  }

  def storeCredentials(username: String, cred: CredentialRegistration) = {
    db.withConnection { implicit c =>
      SQL("INSERT INTO users (username, handle) SELECT {username}, {handle} WHERE NOT EXISTS(SELECT 1 FROM users WHERE username = {username})")
        .on("username" -> username, "handle" -> cred.id.getId.getHex)
        .executeInsert()


      val id = SQL("SELECT id FROM users WHERE username = {username}")
        .on("username" -> username)
        .as(int("id").single)


      SQL("INSERT INTO keys (user, keyId, publicKey, signatureCount) VALUES ({user}, {keyId}, {publicKey}, 0)")
        .on("user" -> id, "keyId" -> cred.credential.getCredentialId.getHex, "publicKey" -> cred.credential.getPublicKeyCose.getHex)
        .executeInsert()
    }
  }

  def updateSignatureCount(keyId: ByteArray, signatureCount: Long): Unit = {
    db.withConnection { implicit c =>
      SQL("UPDATE keys SET signatureCount = {signatureCount} WHERE keyId = {keyId}")
        .on("signatureCount" -> signatureCount, "keyId" -> keyId.getHex)
        .executeUpdate()
    }
  }

  def enrolledKeyCount(username: String): Int = {
    db.withConnection { implicit c =>
      SQL("SELECT COUNT(*) AS keyCount FROM keys JOIN users ON keys.user = users.id WHERE username = {username}")
        .on("username" -> username)
        .as(int("keyCount").single)
    }
  }

  def removeAllEnrolledKeys(username: String): Unit = {
    db.withConnection { implicit c =>
      val id = SQL("SELECT id FROM users WHERE username = {username}")
        .on("username" -> username)
        .as(int("id").single)

      SQL("DELETE FROM keys WHERE user = {id}")
        .on("id" -> id)
        .execute()
    }
  }

}
