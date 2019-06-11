package controllers

import java.net.URI

import javax.inject._
import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._

case class LoginData(username: String, password: String, redirectUrl: Option[String])


@Singleton
class HomeController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  val loginForm: Form[LoginData] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "redirectUrl" -> optional(text)
    )(LoginData.apply)(LoginData.unapply)
  )

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

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        //we don't have the redirect url potentially, so pull it from the session
        BadRequest(views.html.login(formWithErrors, request.session.get("redirectUrl").getOrElse(""), routes.HomeController.login()))
    }


    val success = { data: LoginData =>

      logger.info("Parsed successfully")
      logger.info(s"Username: ${data.username}")

      if (data.username == "test" && data.password == "test") {
        Redirect(data.redirectUrl.getOrElse("/"), SEE_OTHER).withSession(request.session + ("authenticated" -> "ok"))
      } else {
        Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get("redirectUrl").getOrElse(""), routes.HomeController.login()))
          .flashing("message" -> "Incorrect username/password")
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }

  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    val redirectUrl: String = request.queryString.get("redirect").head.head
    Unauthorized(views.html.login(loginForm, redirectUrl, routes.HomeController.login()))
      .withSession(request.session + ("redirectUrl" -> redirectUrl))
  }

  def auth() = Action { implicit request: Request[AnyContent] =>
    logger.warn("Hello world")

    val headerList = request.headers.headers.map { case (k, v) => s"$k => $v"}.mkString("\n")

    val headerMap = request.headers.toMap

    val sourceHost = headerMap("X-Forwarded-Host").head
    val sourcePath = headerMap("X-Forwarded-Uri").headOption

    logger.warn(s"Got source host $sourceHost")

    val uri = new URI("http", sourceHost, sourcePath.getOrElse("/"), null).toURL.toString

    val queryParams: Map[String, Seq[String]] = Map(
      "redirect" -> Seq(uri),
    )

    if (sourcePath.contains("/")) {
      NoContent
    } else {
      if (request.session.data.get("authenticated").contains("ok")) {
        NoContent
      } else {
        Redirect("http://auth.example.com/login", queryParams)
      }

    }
  }
}
