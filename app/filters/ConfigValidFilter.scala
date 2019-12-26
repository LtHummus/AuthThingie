package filters

import akka.stream.Materializer
import config.AuthThingieConfig
import javax.inject.Inject
import play.api.mvc.{Filter, RequestHeader, Result, Results}

import scala.concurrent.Future

class ConfigValidFilter @Inject() (config: AuthThingieConfig, m: Materializer) extends Filter with Results {
  override implicit def mat: Materializer = m //TODO: wtf

  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val errors = config.configErrors
    if (errors.nonEmpty && !rh.path.startsWith("/assets")) {
      Future.successful(InternalServerError(views.html.config_errors(errors)))
    } else {
      next(rh)
    }
  }

}
