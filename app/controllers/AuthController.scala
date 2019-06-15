package controllers

import java.net.URI

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.binary.Base64
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import services.decoding.RequestDecoder
import services.ruleresolving.{Allowed, Denied, RuleResolver}
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

    //figure out if the user is logged in or gave us basic-auth credentials
    val user: Option[User] = (request.session.get("user"), request.headers.get(AUTHORIZATION)) match {
      case (Some(username), _)   => userMatcher.getUser(username) //logged in via session, continue on
      case (_, Some(authHeader)) =>
        val rawAuthData = authHeader.replaceFirst("Basic ", "")
        val Array(username, password) = new String(Base64.decodeBase64(rawAuthData)).split(":", 2)
        userMatcher.validUser(username, password)
      case _                     => None
    }

    //figure out what to do and return the proper response
    resolver.resolveUserAccessForRule(user, rule) match {
      case Allowed =>
        NoContent

      case Denied if user.isEmpty =>
        val destinationUri = new URI(requestInfo.protocol, requestInfo.host, requestInfo.path, null).toURL.toString
        Redirect(s"${config.getSiteUrl}/needed", FOUND).withSession("redirect" -> destinationUri)

      case _ =>
        Unauthorized(views.html.denied("You do not have permission for this resource"))
    }

  }

}
