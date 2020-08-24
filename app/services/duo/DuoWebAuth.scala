package services.duo

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

import config.AuthThingieConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.binary.Hex
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DuoWebAuth @Inject() (config: AuthThingieConfig, ws: WSClient) {

  private val Logger = play.api.Logger(this.getClass)
  private val CanonicalDateTime = DateTimeFormatter.ofPattern("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US)
  private val SigningAlgorithm = "HmacSHA1"

  private val baseUrl = "https://" + config.duoSecurity.get.apiHostname

  implicit class SignableRequest(x: WSRequest) {
    private def urlEncode(input: String): String = {
      URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~");
    }

    private def computeSignature(payload: String): String = {
      val mac = Mac.getInstance(SigningAlgorithm)
      val key = new SecretKeySpec(config.duoSecurity.get.secretKey.getBytes(StandardCharsets.UTF_8), "RAW")
      mac.init(key)
      Hex.encodeHexString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
    }

    def duoSign(methodInput: String, params: Map[String, String] = Map.empty): WSRequest = {
      val dateString = ZonedDateTime.now().format(CanonicalDateTime)
      val method = methodInput.toUpperCase
      val host = x.uri.getHost.toLowerCase
      val path = x.uri.getPath
      val canonicalParams = params
        .toList
        .sortBy(_._1)
        .map{ case(k, v) => s"${urlEncode(k)}=${urlEncode(v)}" }
        .mkString("&")

      val signatureBlock = s"$dateString\n$method\n$host\n$path\n$canonicalParams"
      val signature = computeSignature(signatureBlock)

      val paramedRequest = if (method == "GET" || method == "DELETE") {
        x.withQueryStringParameters(params.toList: _*)
      } else {
        x.withBody(params)
      }

      paramedRequest
        .withMethod(method)
        .withHttpHeaders("Date" -> dateString)
        .withAuth(config.duoSecurity.get.integrationKey, signature, WSAuthScheme.BASIC)
    }
  }

  def ping(implicit ec: ExecutionContext): Future[PingResponse] = {
    ws.url(baseUrl + "/auth/v2/ping").get().map{ resp =>
      (resp.json \ "response").as[PingResponse]
    }
  }

  def check(implicit ec: ExecutionContext): Future[PingResponse] = {
    ws.url(baseUrl + "/auth/v2/check").duoSign("GET").execute().map{ resp =>
      (resp.json \ "response").as[PingResponse]
    }
  }

  def preauth(username: String)(implicit ec: ExecutionContext): Future[PreAuthResponse] = {
    ws.url(baseUrl + "/auth/v2/preauth").duoSign("POST", Map("username" -> username)).execute().map{ resp =>
      (resp.json \ "response").as[PreAuthResponse]
    }
  }

  def authSync(username: String, factor: String, deviceId: String)(implicit ec: ExecutionContext): Future[SyncAuthResult] = {
    val params = Map(
      "username" -> username,
      "factor" -> factor,
      "device" -> deviceId
    )
    ws.url(baseUrl + "/auth/v2/auth").duoSign("POST", params).execute().map{ resp =>
      (resp.json \ "response").as[SyncAuthResult]
    }
  }

  def authAsync(username: String, factor: String, deviceId: String)(implicit ec: ExecutionContext): Future[AsyncAuthResult] = {
    val params = Map(
      "username" -> username,
      "factor" -> factor,
      "device" -> deviceId,
      "async" -> "1"
    )
    ws.url(baseUrl + "/auth/v2/auth").duoSign("POST", params).execute().map{ resp =>
      (resp.json \ "response").as[AsyncAuthResult]
    }
  }

  def authStatus(txid: String)(implicit ec: ExecutionContext): Future[SyncAuthResult] = {
    ws.url(baseUrl + "/auth/v2/auth_status").duoSign("GET", Map("txid" -> txid)).execute().map { resp =>
      (resp.json \ "response").as[SyncAuthResult]
    }
  }

}
