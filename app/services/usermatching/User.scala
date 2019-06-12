package services.usermatching

import services.pathmatching.PathRule

case class User(username: String, passwordHash: String, admin: Boolean, roles: List[String]) {

  def isPermitted(rule: PathRule): Boolean = admin || rule.public || rule.permittedRoles.intersect(roles).nonEmpty
}