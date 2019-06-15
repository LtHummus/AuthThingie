package controllers

import java.net.URI

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.decoding.RequestDecoder
import services.ruleresolving.{Allowed, Denied, RedirectToLogin, RuleResolver}
import services.rules.{PathMatcher, PathRule}
import services.users.{User, UserMatcher}

@Singleton
class AuthController @Inject() (decoder: RequestDecoder,
                                userMatcher: UserMatcher,
                                pathMatcher: PathMatcher,
                                config: TraefikCopConfig,
                                resolver: RuleResolver,
                                cc: ControllerComponents) extends AbstractController(cc) {


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
        Redirect(s"${config.getSiteUrl}/needed").withSession("redirect" -> destinationUri)

      case Denied =>
        Unauthorized(views.html.denied("You do not have permission for this resource"))
    }

  }

}
