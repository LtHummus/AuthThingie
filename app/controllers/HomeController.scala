package controllers

import java.net.URI

import config.TraefikCopConfig
import javax.inject._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import services.decoding.RequestDecoder
import services.ruleresolving.{Allowed, Denied, RedirectToLogin, RuleResolver}
import services.rules.{PathMatcher, PathRule}
import services.users.{User, UserMatcher}

case class LoginData(username: String, password: String, redirectUrl: Option[String])


@Singleton
class HomeController @Inject()(config: TraefikCopConfig,
                               resolver: RuleResolver,
                               decoder: RequestDecoder,
                               pathMatcher: PathMatcher,
                               userMatcher: UserMatcher,
                               cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

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
    Ok(views.html.index(config.getPathRules))
  }

  def login() = Action { implicit request: MessagesRequest[AnyContent] =>
    val error = {
      formWithErrors: Form[LoginData] =>
        //we don't have the redirect url potentially, so pull it from the session
        BadRequest(views.html.login(formWithErrors, request.session.get("redirectUrl").getOrElse(""), routes.HomeController.login()))
    }


    val success = { data: LoginData =>

      if (userMatcher.validUser(data.username, data.password)) {
        Redirect(data.redirectUrl.getOrElse("/"), SEE_OTHER).withSession(request.session + ("authenticated" -> "ok") + ("user" -> data.username))
      } else {
        Logger.warn(s"Bad login attempt for user ${data.username} from ${request.remoteAddress}")
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

    //decode request to find out where the user was heading...
    val requestInfo = decoder.decodeRequestHeaders(request.headers)

    //does that destination match a rule we know about?
    val rule: Option[PathRule] = pathMatcher.getRule(requestInfo.protocol, requestInfo.host, requestInfo.path)

    //figure out if the user is logged in
    val user: Option[User] = request.session.get("user") match {
      case None           => None
      case Some(username) => userMatcher.getUser(username)
    }

    //figure out what to do and return the proper response
    resolver.resolveUserAccessForRule(user, rule) match {
      case Allowed =>
        NoContent

      case RedirectToLogin =>
        val destinationUri = new URI(requestInfo.protocol, requestInfo.host, requestInfo.path, null).toURL.toString
        val queryParams: Map[String, Seq[String]] = Map("redirect" -> Seq(destinationUri))
        Redirect(s"${config.getSiteUrl}/login", queryParams)

      case Denied =>
        Unauthorized(views.html.denied("You do not have permission for this resource"))
    }

  }
}
