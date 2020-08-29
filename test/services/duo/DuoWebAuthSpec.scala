package services.duo

import java.util.concurrent.Executors

import akka.util.Helpers.Requiring
import config.{AuthThingieConfig, DuoSecurityConfig}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.sird._
import play.core.server.{Server, ServerConfig}
import play.api.test._
import play.filters.HttpFiltersComponents

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
class DuoWebAuthSpec extends PlaySpec with MockitoSugar {
  import scala.concurrent.ExecutionContext.Implicits.global

  def withMockDuoService[T](config: AuthThingieConfig)(block: DuoWebAuth => T): T = {
    Server.withApplicationFromContext() { context =>
      new BuiltInComponentsFromContext(context) with HttpFiltersComponents {
        override def httpFilters: Seq[EssentialFilter] = Seq(securityHeadersFilter, allowedHostsFilter)
        override def router: Router = Router.from {
          case GET(p"/auth/v2/ping") =>
            Action { req =>
              Results.Ok(Json.obj("response" -> PingResponse(System.currentTimeMillis())))
            }

          case GET(p"/auth/v2/check") =>
            Action { req =>
              Results.Ok(Json.obj("response" -> PingResponse(System.currentTimeMillis())))
            }

          case POST(p"/auth/v2/preauth") =>
            Action { req =>
              Results.Ok(Json.obj("response" -> PreAuthResponse(
                "auth",
                "test status",
                List(
                  Device(List("push"), "device-1", None, "abcdefg", "12345", "phone")
                )
              )))
            }

          case POST(p"/auth/v2/auth") =>
            Action { req =>
              Results.Ok(Json.obj("response" -> AsyncAuthResult("test-transaction")))
          }

          case GET(p"/auth/v2/auth_status") =>
            Action { res =>
              Results.Ok(Json.obj("response" -> SyncAuthResult("allow", "allow", "foo")))
            }
        }
      }.application
    } { implicit port =>
      WsTestClient.withClient { client =>
        block(new DuoWebAuth(config, client))
      }
    }
  }

  "DuoWebAuth" should {
    "successfully ping" in {
      val fakeConfig = mock[AuthThingieConfig]
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ", "secret", ""))

      withMockDuoService(fakeConfig) { client =>
        val res = Await.result(client.ping, 10.seconds)
        res.time mustBe System.currentTimeMillis() +- 1.second.toMillis
      }
    }

    "successfully check" in {
      val fakeConfig = mock[AuthThingieConfig]
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ", "secret", ""))

      withMockDuoService(fakeConfig) { client =>
        val res = Await.result(client.check, 10.seconds)
        res.time mustBe System.currentTimeMillis() +- 1.second.toMillis
      }
    }

    "successfully preauth" in {
      val fakeConfig = mock[AuthThingieConfig]
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ", "secret", ""))

      withMockDuoService(fakeConfig) { client =>
        val res = Await.result(client.preauth("test-user"), 10.seconds)
        res.devices must have length 1
      }
    }

    "successfully async auth" in {
      val fakeConfig = mock[AuthThingieConfig]
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ", "secret", ""))

      withMockDuoService(fakeConfig) { client =>
        val res = Await.result(client.authAsync("test-user", "push", "fooo"), 10.seconds)
        res.txid mustBe "test-transaction"
      }
    }

    "successfully check transaction" in {
      val fakeConfig = mock[AuthThingieConfig]
      fakeConfig.duoSecurity returns Some(DuoSecurityConfig("integ", "secret", ""))

      withMockDuoService(fakeConfig) { client =>
        val res = Await.result(client.authStatus("test-transaction"), 10.seconds)
        res.status mustBe "allow"
      }
    }
  }
}
