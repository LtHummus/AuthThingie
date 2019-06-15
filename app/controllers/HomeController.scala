package controllers

import config.TraefikCopConfig
import javax.inject._
import play.api.mvc._
case class LoginData(username: String, password: String, redirectUrl: Option[String])


@Singleton
class HomeController @Inject()(config: TraefikCopConfig,
                               cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)



  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index(request.session.get("user")))
  }



}
