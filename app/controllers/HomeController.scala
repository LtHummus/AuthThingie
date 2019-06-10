package controllers

import javax.inject._
import play.api._
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) with Logging {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def login() = Action { implicit request: Request[AnyContent] =>
    Ok("???")
  }

  def showLoginForm() = Action { implicit request: Request[AnyContent] =>
    val host = request.queryString.get("host").head.head
    val path = request.queryString.get("path").head.head
    Unauthorized(views.html.login(host, path))
  }

  def auth() = Action { implicit request: Request[AnyContent] =>
    logger.warn("Hello world")

    val headerList = request.headers.headers.map { case (k, v) => s"$k => $v"}.mkString("\n")

    val headerMap = request.headers.toMap

    val sourceHost = headerMap("X-Forwarded-Host").head
    val sourcePath = headerMap("X-Forwarded-Uri").headOption

    logger.warn(s"Got source host $sourceHost")

    val queryParams: Map[String, Seq[String]] = Map(
      "host" -> Seq(sourceHost),
      "path" -> Seq(sourcePath.getOrElse("/"))
    )

    if (sourcePath.contains("/")) {
      Ok("")
    } else {
      Redirect("http://auth.example.com/login", queryParams)
    }
  }
}
