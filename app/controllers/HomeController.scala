package controllers

import java.time.{Duration, Instant, ZonedDateTime}

import config.AuthThingieConfig
import javax.inject._
import play.api.mvc._
import services.users.UserMatcher
import util.SessionImplicits._

@Singleton
class HomeController @Inject()(userMatcher: UserMatcher,
                               cc: MessagesControllerComponents)(implicit config: AuthThingieConfig) extends MessagesAbstractController(cc) {

  def index() = Action { implicit request: Request[AnyContent] =>
    val loggedInUser = for {
      sessionUser      <- request.session.getUserAuthedWithin(config.sessionTimeout)
      knownUser        <- userMatcher.getUser(sessionUser)
    } yield knownUser

    val isAdmin = loggedInUser.exists(_.admin)
    val rules = if (isAdmin) config.pathRules else List()
    val allUsers = if (isAdmin) config.users else List()
    val settings = if (isAdmin) Some(config.asEntries) else None
    val loginTime = request.session.get("authTime").flatMap(_.toLongOption).map(x => ZonedDateTime.ofInstant(Instant.ofEpochMilli(x), config.timeZone))
    val loginDuration = loginTime.map(x => Duration.between(x, ZonedDateTime.now(config.timeZone)))

    Ok(views.html.index(loggedInUser, rules, allUsers, isAdmin && !config.isUsingNewConfig, config.siteName, !config.hasTimeoutSetProperly, loginTime, settings, loginDuration))
  }



}
