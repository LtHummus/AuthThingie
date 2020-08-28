package modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.duo.DuoAsyncActor

class DuoActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[DuoAsyncActor]("duo-async-actor")
  }
}
