package controllers

import java.net.URI

import config.TraefikCopConfig
import javax.inject._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import services.pathmatching.{PathMatcher, PathRule}
import services.usermatching.{User, UserMatcher}

case class LoginData(username: String, password: String, redirectUrl: Option[String])


@Singleton
class HomeController @Inject()(config: TraefikCopConfig, pathMatcher: PathMatcher, userMatcher: UserMatcher, cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

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
    Ok(views.html.index(pathMatcher.Rules))
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        //we don't have the redirect url potentially, so pull it from the session
        BadRequest(views.html.login(formWithErrors, request.session.get("redirectUrl").getOrElse(""), routes.HomeController.login()))
    }


    val success = { data: LoginData =>

      Logger.info("Parsed successfully")
      Logger.info(s"Username: ${data.username}")

      if (userMatcher.validUser(data.username, data.password)) {
        Redirect(data.redirectUrl.getOrElse("/"), SEE_OTHER).withSession(request.session + ("authenticated" -> "ok") + ("user" -> data.username))
      } else {
        Unauthorized(views.html.login(loginForm.fill(data.copy(password = "")), request.session.get("redirectUrl").getOrElse(""), routes.HomeController.login()))
          .flashing("message" -> "Incorrect username/password")
      }
    }

    loginForm.bindFromRequest.fold(error, success)
  }

  def logout() = Action { implicit request: MessagesRequest[AnyContent] =>
    Logger.info("Logging out")
    Ok("Logged out").withNewSession
  }

  def showLoginForm() = Action { implicit request: MessagesRequest[AnyContent] =>
    val redirectUrl: String = request.queryString.get("redirect").head.head
    Unauthorized(views.html.login(loginForm, redirectUrl, routes.HomeController.login()))
      .withSession(request.session + ("redirectUrl" -> redirectUrl))
  }

  def auth() = Action { implicit request: Request[AnyContent] =>
    val headerMap = request.headers.toMap

    val sourceHost = headerMap("X-Forwarded-Host").head
    val sourcePath = headerMap("X-Forwarded-Uri").headOption

    val uri = new URI("http", sourceHost, sourcePath.getOrElse("/"), null).toURL.toString

    val queryParams: Map[String, Seq[String]] = Map(
      "redirect" -> Seq(uri),
    )

    val user: Option[User] = request.session.get("user") match {
      case None           => None
      case Some(username) => userMatcher.getUser(username)
    }


    val rule: Option[PathRule] = pathMatcher.getRule("https", sourceHost, sourcePath.getOrElse("/"))

    (user, rule) match {
      case (None, Some(r)) if r.public             => NoContent //permitted, not logged in, but destination is public
      case (None, Some(r)) if !r.public            => Redirect(s"${config.getSiteUrl}/login", queryParams) //redirect to login, user needs to log in
      case (None, None)                            => Redirect(s"${config.getSiteUrl}/login", queryParams) //not logged in, wants access to admin only page. Redirect to login
      case (Some(u), None) if !u.admin             => Unauthorized(views.html.denied("You do not have permission for this resource"))
      case (Some(u), None) if u.admin              => NoContent //permitted, no rule, but user is admin
      case (Some(u), Some(r)) if u.isPermitted(r)  => NoContent //user is allowed
      case (Some(u), Some(r)) if !u.isPermitted(r) => Unauthorized(views.html.denied("You do not have permission for this resource"))
    }

  }
}
