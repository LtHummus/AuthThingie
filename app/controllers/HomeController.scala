package controllers

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

import config.AuthThingieConfig
import javax.inject._
import play.api.mvc._
import services.users.UserMatcher

import util.SessionImplicits._

@Singleton
class HomeController @Inject()(userMatcher: UserMatcher,
                               cc: MessagesControllerComponents)(implicit config: AuthThingieConfig) extends MessagesAbstractController(cc) {

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
      sessionUser      <- request.session.getUserAuthedWithin(config.sessionTimeout)
      knownUser        <- userMatcher.getUser(sessionUser)
    } yield knownUser

    val isAdmin = loggedInUser.exists(_.admin)
    val rules = if (isAdmin) config.pathRules else List()
    val allUsers = if (isAdmin) config.users else List()
    val settings = if (isAdmin) Some(config.asMap) else None
    val loginTime = request.session.get("authTime").flatMap(_.toLongOption).map(x => ZonedDateTime.ofInstant(Instant.ofEpochMilli(x), config.timeZone))

    Ok(views.html.index(loggedInUser, rules, allUsers, isAdmin && !config.isUsingNewConfig, config.siteName, !config.hasTimeoutSetProperly, loginTime, settings))
  }



}
