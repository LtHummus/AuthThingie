package controllers

import at.favre.lib.crypto.bcrypt.BCrypt
import config.AuthThingieConfig
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, MessagesAbstractController, MessagesControllerComponents, MessagesRequest, Request}
import services.role.RoleDatabase
import services.rules.RuleDatabase
import services.storage.SqlStorageService
import services.users.UserDatabase

import javax.inject.{Inject, Singleton}

case class SetupData(username: String, password: String, passwordConfirm: String)

@Singleton
class FirstTimeSetupController @Inject() (storage: SqlStorageService,
                                          userDatabase: UserDatabase,
                                          ruleDatabase: RuleDatabase,
                                          roleDatabase: RoleDatabase,
                                          config: AuthThingieConfig,
                                          cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {
  private val DefaultCost = 10

  val setupForm: Form[SetupData] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "passwordConfirm" -> nonEmptyText
    )(SetupData.apply)(SetupData.unapply)
  )


  def index() = Action { implicit request: MessagesRequest[AnyContent] =>
    if (config.users.nonEmpty) {
      Ok(views.html.migration(config.users, config.pathRules))
    } else {
      Ok(views.html.first_time_setup(setupForm))
    }
  }

  def completeMigration() = Action { implicit request: MessagesRequest[AnyContent] =>
    // this is going to have a lot of "sub-optimal" queries and batching, but two things: 1) it's SQLite, so who cares,
    // 2) this is only run (ideally) once per setup
    if (userDatabase.getAllUsers().nonEmpty || ruleDatabase.getRules().nonEmpty || roleDatabase.listRoles().nonEmpty) {
      Forbidden(views.html.denied("Database currently has users and rules in it. Will not do migration unless database is empty"))
    } else {
      val rules = config.pathRules
      val users = config.users

      val allRoles = rules.flatMap(_.permittedRoles).toSet ++ users.flatMap(_.roles).toSet

      allRoles.foreach(roleDatabase.createRole)

      rules.foreach(rule => {
        ruleDatabase.createRule(rule)
        rule.permittedRoles.foreach(role => roleDatabase.attachRoleToRule(rule.name, role))
      })

      users.foreach(user => {
        userDatabase.createUser(user)
        user.roles.foreach(role => roleDatabase.attachRoleToUser(user.username, role))
      })

      Redirect(routes.HomeController.index())
    }


  }

  def completeSetup() = Action { implicit request: MessagesRequest[AnyContent] =>
    val errored = {
      formWithErrors: Form[SetupData] =>
        Ok(views.html.first_time_setup(formWithErrors))
    }

    val successful = { data: SetupData =>
      if (data.password != data.passwordConfirm) {
        Ok(views.html.first_time_setup(setupForm.fill(data)))
      } else {
        val passwordHash = BCrypt.withDefaults().hashToString(DefaultCost, data.password.toCharArray)
        userDatabase.createUser(data.username, passwordHash, isAdmin = true)
        Redirect(routes.HomeController.index())
      }
    }

    setupForm.bindFromRequest.fold(errored, successful)
  }
}
