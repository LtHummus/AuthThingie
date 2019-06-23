package services.decoding

import java.net.{URI, URL}

case class RequestInfo(protocol: String, host: String, path: String) {
  def toUri: URI = {
    val hostParts = host.split(":", 2)
    if (hostParts.length == 2) {
      //we have a host as part of our port, so take that in to account
      new URI(protocol, null, hostParts(0), hostParts(1).toInt, path, null, null)
    } else {
      new URI(protocol, host, path, null)
    }
  }

  def toUrl: URL = toUri.toURL

  override def toString: String = toUrl.toString
}