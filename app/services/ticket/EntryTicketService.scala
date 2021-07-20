package services.ticket

import com.github.blemale.scaffeine.Scaffeine
import services.users.User

import java.security.SecureRandom
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt


@Singleton
class EntryTicketService @Inject () () {
  private val Cache = Scaffeine()
    .expireAfterWrite(5.minutes)
    .build[String, EntryTicket]()

  private val Random = new SecureRandom()
  private val KeyLengthBytes = 32
  private val TicketEncoder = Base64.getUrlEncoder.withoutPadding()

  private def generateKey(): String = {
    val ticketIdBytes = Array.ofDim[Byte](KeyLengthBytes)
    Random.nextBytes(ticketIdBytes)

    TicketEncoder.encodeToString(ticketIdBytes)
  }

  def createTicket(result: String, user: User, redirectUrl: String): String = {
    val ticket = EntryTicket(result, user.username, redirectUrl, System.currentTimeMillis())
    storeTicket(ticket)
  }

  def storeTicket(ticket: EntryTicket): String = {
    val key = generateKey()
    Cache.put(key, ticket)

    key
  }

  def retrieveTicket(key: String): Option[EntryTicket] = {
    val ticket = Cache.getIfPresent(key)
    Cache.invalidate(key)
    ticket
  }

}
