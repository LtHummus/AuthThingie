package controllers

import config.AuthThingieConfig
import javax.inject._
import play.api.mvc._
import services.users.UserMatcher

@Singleton
class HomeController @Inject()(config: AuthThingieConfig,
                               userMatcher: UserMatcher,
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
    val loggedInUser = for {
      sessionUser <- request.session.get("user")
      knownUser   <- userMatcher.getUser(sessionUser)
    } yield knownUser

    val isAdmin = loggedInUser.exists(_.admin)
    val rules = if (isAdmin) config.getPathRules else List()
    val allUsers = if (isAdmin) config.getUsers else List()

    Ok(views.html.index(loggedInUser, rules, allUsers, isAdmin && !config.isUsingNewConfig))
  }



}
