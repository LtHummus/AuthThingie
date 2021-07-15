package services.webauthn

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.yubico.webauthn.{AssertionRequest, FinishAssertionOptions, FinishRegistrationOptions, RegisteredCredential, RelyingParty, StartAssertionOptions, StartRegistrationOptions}
import com.yubico.webauthn.data.{AuthenticatorAssertionResponse, AuthenticatorSelectionCriteria, ByteArray, PublicKeyCredential, PublicKeyCredentialCreationOptions, PublicKeyCredentialDescriptor, RelyingPartyIdentity, UserIdentity, UserVerificationRequirement}
import com.yubico.webauthn.exception.{AssertionFailedException, RegistrationFailedException}
import config.AuthThingieConfig
import org.apache.commons.lang3.RandomStringUtils
import org.bouncycastle.util.encoders.Hex

import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.jdk.OptionConverters.{RichOption, RichOptional}
import scala.util.Random

@Singleton
class WebAuthnService @Inject() (config: AuthThingieConfig, repo: SqlCredentialRepo) {
  private val Logger = play.api.Logger(this.getClass)

  private val identity = RelyingPartyIdentity.builder()
    .id(config.webauthn.map(_.rp).getOrElse("disabled"))
    .name(config.webauthn.map(_.displayName).getOrElse("disabled"))
    .build()

  private val party = RelyingParty.builder()
    .identity(identity)
    .credentialRepository(repo)
    .validateSignatureCounter(true)
    .build()

  private val mapper = new ObjectMapper()
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    .setSerializationInclusion(Include.NON_ABSENT)
    .registerModule(new Jdk8Module())

  private val cache = collection.mutable.HashMap.empty[String, PublicKeyCredentialCreationOptions]
  private val authCache = collection.mutable.HashMap.empty[String, AssertionRequest]

  def startRegistration(name: String, asResident: Boolean = false): String = {
    val handle = repo.getUserHandleForUsername(name).toScala match {
      case Some(h) => h.getBytes
      case None =>
        val h = Array.fill[Byte](32)(0)
        new Random().nextBytes(h) // TODO: make this crypto secure
        h
    }


    val userId = UserIdentity.builder()
      .name(name)
      .displayName(name)
      .id(new ByteArray(handle))
      .build()

    val authenticatorSelection = AuthenticatorSelectionCriteria.builder()
      .userVerification(UserVerificationRequirement.DISCOURAGED)
      .requireResidentKey(asResident)
      .build()

    val registrationOptions = StartRegistrationOptions.builder()
      .user(userId)
      .authenticatorSelection(authenticatorSelection)
      .build()

    val req = party.startRegistration(registrationOptions)

    cache.put(name, req)
    mapper.writeValueAsString(req)

  }

  def completeRegistration(username: String, payload: String): Boolean = {
    val registrationResponse = PublicKeyCredential.parseRegistrationResponseJson(payload)

    val options = FinishRegistrationOptions.builder()
      .request(cache(username))
      .response(registrationResponse)
      .build()

    try {
      val result = party.finishRegistration(options)
      val registeredCredential = RegisteredCredential.builder()
        .credentialId(result.getKeyId.getId)
        .userHandle(cache(username).getUser.getId)
        .publicKeyCose(result.getPublicKeyCose)
        .build()
      val cr = CredentialRegistration(registeredCredential, cache(username).getUser, Some(username), result.getAttestationMetadata.toScala)
      repo.storeCredentials(username, cr)
      true
    } catch {
      case e: RegistrationFailedException =>
        Logger.warn("could not complete registration", e)
        false
    }

  }

  def startResidentAssert(): (String, String) = {
    val key = RandomStringUtils.randomAlphanumeric(32)
    val options = StartAssertionOptions.builder()
      .build()

    val req = party.startAssertion(options)
    authCache.put(key, req)
    (mapper.writeValueAsString(req), key)
  }

  def finishResidentAssert(payload: String, key: String): Option[String] = {
    val pkc = PublicKeyCredential.parseAssertionResponseJson(payload)
    val potentialKeys = repo.lookupAll(pkc.getId).asScala
    val response = pkc.getResponse

    if (potentialKeys.isEmpty) {
      Logger.info("Did not find any potentially matching keys. Stopping")
      None
    } else {
      val userHandle = potentialKeys.head.getUserHandle
      val authRequest = authCache(key)

      // for some reason, the library we're using requires that the user handle is in the response in order
      // to check it, but we don't have a username because it's usernameless login, so we can
      // just fetch the userid from the database based on the key and then go from there and rebuild all
      // the data to make it all work
      val rebuildResponse = AuthenticatorAssertionResponse.builder()
        .authenticatorData(response.getAuthenticatorData)
        .clientDataJSON(response.getClientDataJSON)
        .signature(response.getSignature)
        .userHandle(userHandle)
        .build()
      val rebuiltPkc = PublicKeyCredential.builder()
        .id(pkc.getId)
        .response(rebuildResponse)
        .clientExtensionResults(pkc.getClientExtensionResults)
        .build()
      val assertion = FinishAssertionOptions.builder()
        .request(authRequest)
        .response(rebuiltPkc)
        .build()

      try {
        val result = party.finishAssertion(assertion)
        repo.updateSignatureCount(result.getCredentialId, result.getSignatureCount)
        Some(result.getUsername)
      } catch {
        case e: AssertionFailedException =>
          Logger.warn("could not validate", e)
          None
      }
    }
  }

  def startAssertion(username: String): String = {
    Logger.info(s"Starting auth for $username")
    val options = StartAssertionOptions.builder()
      .username(username)
      .userVerification(UserVerificationRequirement.DISCOURAGED)
      .build()
    val req = party.startAssertion(options)
    authCache.put(username, req)

    mapper.writeValueAsString(req)
  }

  def finishAssertion(username: String, payload: String): Boolean = {
    val pkc = PublicKeyCredential.parseAssertionResponseJson(payload)
    val assertion = FinishAssertionOptions.builder()
      .request(authCache(username))
      .response(pkc)
      .build()

    try {
      val result = party.finishAssertion(assertion)
      repo.updateSignatureCount(result.getCredentialId, result.getSignatureCount)
      result.isSuccess
    } catch {
      case e: AssertionFailedException =>
        Logger.warn("could not validate", e)
        false
    }
  }

  def enrolledKeyCount(username: String): Int = repo.enrolledKeyCount(username)
  def listEnrolledKeys(username: String): List[PublicKeyCredentialDescriptor] = repo.getCredentialIdsForUsername(username).asScala.toList
  def unenrollUser(username: String): Unit = repo.removeAllEnrolledKeys(username)

}
