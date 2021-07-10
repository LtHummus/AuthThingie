package controllers

import java.time.{Duration, Instant, ZonedDateTime}
import config.AuthThingieConfig

import javax.inject._
import play.api.mvc._
import services.users.UserMatcher
import services.webauthn.WebAuthnService
import util.SessionImplicits._

@Singleton
class HomeController @Inject()(userMatcher: UserMatcher,
                               webauthn: WebAuthnService,
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
    val enrolled = request.session.get("user").map(webauthn.listEnrolledKeys).getOrElse(List.empty).map(_.getId).toList

    Ok(views.html.index(loggedInUser, rules, allUsers, isAdmin && !config.isUsingNewConfig, config.siteName, !config.hasTimeoutSetProperly, loginTime, settings, loginDuration, enrolled, config.webauthn.isDefined))
  }



}
