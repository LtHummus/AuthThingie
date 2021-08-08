package util

import play.api.mvc.Results.Forbidden
import play.api.mvc.{Request, Result}
import services.ruleresolving.Denied
import services.users.{User, UserMatcher}
import util.SessionImplicits._

object RequestImplicits {
  def withLoggedInUser(f: User => Result)(implicit request: Request[_], userMatcher: UserMatcher): Result = {
    request.session.getAuthuedUser match {
      case None    => Forbidden(views.html.denied("You must be logged in to access this page"))
      case Some(u) => f(u)
    }
  }
}
