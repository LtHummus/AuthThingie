package services.decoding

import config.AuthThingieConfig
import javax.inject.{Inject, Singleton}
import play.api.mvc.Headers

@Singleton
class RequestDecoder @Inject() (config: AuthThingieConfig) {

  private val Logger = play.api.Logger(this.getClass)

  def decodeRequestHeaders(headers: Headers): RequestInfo = {
    val optionalProtocol = headers.get("X-Forwarded-Proto")
    val optionalHost = headers.get("X-Forwarded-Host")
    val optionalPath = headers.get("X-Forwarded-Uri")

    (optionalProtocol, optionalHost, optionalPath) match {
      case (Some(protocol), Some(host), Some(path)) =>
        if (config.forceRedirectToHttps && protocol == "http") {
          RequestInfo("https", host, path)
        } else {
          RequestInfo(protocol, host, path)
        }
      case _ =>
        Logger.error(s"Missing some URL components: protocol = $optionalProtocol; host = $optionalHost; path = $optionalPath")
        throw new IllegalArgumentException("missing forwarding information")
    }
  }
}
