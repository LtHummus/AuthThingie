package services.ticket

import com.github.blemale.scaffeine.Scaffeine
import play.api.Logger
import services.users.{User, UserMatcher}

import java.security.SecureRandom
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt


@Singleton
class EntryTicketService @Inject () (userService: UserMatcher) {
  private val Cache = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, EntryTicket]()

  private val Logger = play.api.Logger(this.getClass)
  private val Random = new SecureRandom()
  private val KeyLengthBytes = 32
  private val TicketEncoder = Base64.getUrlEncoder.withoutPadding()

  private def generateKey(): String = {
    val ticketIdBytes = Array.ofDim[Byte](KeyLengthBytes)
    Random.nextBytes(ticketIdBytes)

    TicketEncoder.encodeToString(ticketIdBytes)
  }

  def createTicket(result: String, user: User, redirectUrl: String): Option[String] = {
    val ticket = EntryTicket(result, user.username, redirectUrl, System.currentTimeMillis())
    Some(storeTicket(ticket))
  }

  def createTicket(result: String, username: String, redirectUrl: String): Option[String] = {
    userService.getUser(username).map(u => {
      val ticket = EntryTicket(result, u.username, redirectUrl, System.currentTimeMillis())
      storeTicket(ticket)
    })
  }

  def storeTicket(ticket: EntryTicket): String = {
    val key = generateKey()
    Cache.put(key, ticket)
    Logger.info(s"Storing key $key")
    key
  }

  def retrieveTicket(key: String): Option[EntryTicket] = {
    Logger.info(s"Looking for ticket $key")
    val ticket = Cache.getIfPresent(key)
    Cache.invalidate(key)
    ticket
  }

}
