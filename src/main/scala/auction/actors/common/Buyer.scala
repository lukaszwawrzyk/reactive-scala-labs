package auction.actors.common

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auction.Config
import auction.actors.common.Buyer._
import auction.model.Item

import scala.concurrent.duration.FiniteDuration


object Buyer {
  case class AuctionWon(item: Item, price: BigDecimal)
  case object Stop

  private case object TimeToBid

  private val ZeroDuration = FiniteDuration(0, TimeUnit.SECONDS)

  def props(auctions: TraversableOnce[ActorRef]): Props = Props(new Buyer(auctions.toList))
}

class Buyer(private val auctions: List[ActorRef]) extends Actor {
  private val bidTimerToken = {
    context.system.scheduler.schedule(
      initialDelay = ZeroDuration,
      interval = Config.BuyerBiddingInterval,
      receiver = self,
      message = TimeToBid
    )(context.dispatcher)
  }

  override def receive: Receive = LoggingReceive {
    case TimeToBid =>
      randomAuction() ! randomBid()
    case AuctionWon(Item(itemName), price) =>
      println(s"I (${context.self.path.name}) bought $itemName for $price")
    case Stop =>
      bidTimerToken.cancel()
      context stop self
  }

  private def randomAuction() = {
    val index = scala.util.Random.nextInt(auctions.size)
    auctions(index)
  }

  private def randomBid() = {
    val bidValueInCents = BigDecimal(scala.util.Random.nextInt(200000))
    val bidValue = bidValueInCents / 100
    Auction.Bid(bidValue)
  }
}
