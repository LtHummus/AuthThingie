package services.ruleresolving

import javax.inject.{Inject, Singleton}
import services.rules.PathRule
import services.users.User

@Singleton
class RuleResolver @Inject() () {

  def resolveUserAccessForRule(user: Option[User], rule: Option[PathRule]): ResolvedPermission = {
    (user, rule) match {
      case (None, Some(r)) if r.public             => Allowed //permitted, not logged in, but destination is public
      case (None, Some(r)) if !r.public            => RedirectToLogin //redirect to login, user needs to log in
      case (None, None)                            => RedirectToLogin //not logged in, wants access to admin only page. Redirect to login
      case (Some(u), None) if !u.admin             => Denied //user is logged in, but no rule is found. Default to admin only
      case (Some(u), None) if u.admin              => Allowed //permitted, no rule, but user is admin
      case (Some(u), Some(r)) if u.isPermitted(r)  => Allowed //user is allowed
      case (Some(u), Some(r)) if !u.isPermitted(r) => Denied //user is not allowed
    }
  }
}

sealed trait ResolvedPermission
case object Allowed extends ResolvedPermission
case object RedirectToLogin extends ResolvedPermission
case object Denied extends ResolvedPermission