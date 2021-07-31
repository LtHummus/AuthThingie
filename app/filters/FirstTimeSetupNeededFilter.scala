package filters

import akka.stream.Materializer
import controllers.routes
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import services.storage.SqlStorageService

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class FirstTimeSetupNeededFilter @Inject() (sqlStorageService: SqlStorageService, m: Materializer) extends Filter with Results {
  override implicit def mat: Materializer = m //TODO: wtf

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    if (sqlStorageService.setupNeeded() && !rh.path.startsWith("/setup") && !rh.path.startsWith("/assets")) {
      Future.successful(Redirect("/setup")) //TODO: use route lookup instead
    } else {
      f(rh)
    }
  }
}
