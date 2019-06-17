package controllers

import java.net.URI

import config.TraefikCopConfig
import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.binary.Base64
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import play.api.libs.json._
import services.decoding.RequestDecoder
import services.ruleresolving.{Allowed, Denied, RuleResolver}
import services.rules.{PathMatcher, PathRule}
import services.users.{User, UserMatcher}

import scala.util.Try

@Singleton
class AuthController @Inject() (decoder: RequestDecoder,
                                userMatcher: UserMatcher,
                                pathMatcher: PathMatcher,
                                config: TraefikCopConfig,
                                resolver: RuleResolver,
                                cc: ControllerComponents) extends AbstractController(cc) {

  private val Logger = play.api.Logger(this.getClass)

  sealed trait CredentialSource
  case object Session extends CredentialSource
  case object BasicAuth extends CredentialSource
  case object NoCredentials extends CredentialSource

  private def pullLoginInfoFromRequest[T](implicit request: Request[T]): (Option[User], CredentialSource) = {
    (request.session.get("user"), request.headers.get(AUTHORIZATION)) match {
      case (Some(username), _)   => (userMatcher.getUser(username), Session) //logged in via session, continue on
      case (_, Some(authHeader)) =>
        val rawAuthData = authHeader.replaceFirst("Basic ", "")
        val Array(username, password) = new String(Base64.decodeBase64(rawAuthData)).split(":", 2)
        val potentialUser = userMatcher.validUser(username, password)
        if (potentialUser.isEmpty) {
          Logger.warn(s"Bad login attempt for user $username from ${request.headers("X-Forwarded-For")}")
        }
        (potentialUser, BasicAuth)
      case _                     => (None, NoCredentials)
    }
  }

  def testUrl() = Action { implicit request: Request[AnyContent] =>
    val (user, _) = pullLoginInfoFromRequest
    Logger.debug(s"Logged in user: ${user.map(_.username)}")

    if (user.isEmpty) {
      Unauthorized(Json.toJson(Map("error" -> "Not logged in")))
    } else {
      val urlFromQueryString = request.queryString.get("url").map(_.head)

      val parsedUrl = for {
        urlFromRequest <- urlFromQueryString.toRight(new Exception("No valid URL specified"))
        parsedUrl <- Try(new URI(urlFromRequest)).toEither
      } yield parsedUrl

      parsedUrl match {
        case Left(error) => BadRequest(Json.toJson(Map("error" -> error.getMessage)))
        case Right(url) =>
          val matchedRule = pathMatcher.getRule(url)
          Ok(Json.toJson(Map("rule_name" -> matchedRule.map(_.name).orNull)))
      }
    }
  }

  def auth() = Action { implicit request: Request[AnyContent] =>
    //decode request to find out where the user was heading...
    val requestInfo = decoder.decodeRequestHeaders(request.headers)
    Logger.debug(s"Decoded request: protocol = ${requestInfo.protocol}; host = ${requestInfo.host}; path = ${requestInfo.path}")

    //does that destination match a rule we know about?
    val rule: Option[PathRule] = pathMatcher.getRule(requestInfo.protocol, requestInfo.host, requestInfo.path)
    Logger.debug(s"Detected rule: ${rule.map(_.name)}")

    //figure out if the user is logged in or gave us basic-auth credentials
    val (user, credentialSource) = pullLoginInfoFromRequest
    Logger.debug(s"Logged in user: ${user.map(_.username)}")

    //figure out what to do and return the proper response
    resolver.resolveUserAccessForRule(user, rule) match {
      case Allowed =>
        Logger.debug("Access allowed")
        NoContent

      case Denied if credentialSource == BasicAuth || user.isDefined =>
        Logger.debug("Access denied. Showing error")
        Unauthorized(views.html.denied("You do not have permission for this resource"))

      case _ =>
        Logger.debug("Access denied, redirecting to login page")
        val destinationUri = new URI(requestInfo.protocol, requestInfo.host, requestInfo.path, null).toURL.toString
        Redirect(s"${config.getSiteUrl}/needed", FOUND).withSession("redirect" -> destinationUri)

    }

  }

}
