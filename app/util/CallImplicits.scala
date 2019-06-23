package util

import java.net.URLEncoder

import play.api.mvc.Call

object CallImplicits {
  implicit class QueryStringableCall(x: Call) {
    def appendQueryString(queryString: Map[String, Seq[String]]): Call = {
      val newUrl = x.url + Option(queryString)
        .filterNot(_.isEmpty)
        .map { params =>
          (if (x.url.contains("?")) "&" else "?") + params.toSeq
            .flatMap { pair =>
              pair._2.map(value => (pair._1 + "=" + URLEncoder.encode(value, "utf-8")))
            }
            .mkString("&")
        }
        .getOrElse("")

      x.copy(url = newUrl)
    }
  }
}
