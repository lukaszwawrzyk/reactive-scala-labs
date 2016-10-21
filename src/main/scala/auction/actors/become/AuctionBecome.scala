package auction.actors.become

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import auction.Config
import auction.actors.become.AuctionBecome._
import auction.actors.common.Auction._
import auction.actors.common.{Auction, Buyer, Seller}
import auction.model.{Item, Money}

import scala.concurrent.duration.FiniteDuration

object AuctionBecome {
  def props(item: Item): Props = Props(new AuctionBecome(item))

  private case class CurrentBid(value: Money, buyer: ActorRef)

  private case object BiddingTimePassed
  private case object DeleteTimePassed
}

class AuctionBecome(val item: Item) extends Actor with Auction {

  override def receive: Receive = idle

  val idle: Receive = {
    case Start =>
      registerInAuctionSearch()
      initialize()
  }

  val created: Receive = {
    case BiddingTimePassed =>
      val cancelToken = startDeleteTimer()
      unregisterInAuctionSearch()
      context become ignored(cancelToken)
    case Bid(value) =>
      context become activated(CurrentBid(value, sender))
  }

  def ignored(cancelDeleteTimerToken: Cancellable): Receive = {
    case DeleteTimePassed =>
      println(s"Auction for ${item.name} ended without buyer")
      auctionEnded()
    case Relist =>
      println(s"Auction for ${item.name} was relisted")
      cancelDeleteTimerToken.cancel()
      registerInAuctionSearch()
      initialize()
  }

  def activated(currentBid: CurrentBid): Receive = {
    case Bid(value) if value > currentBid.value =>
      println(s"item ${item.name} now has value $value")
      currentBid.buyer ! Buyer.OfferOverbid(value)
      context become activated(CurrentBid(value, sender))
    case BiddingTimePassed =>
      currentBid.buyer ! Buyer.AuctionWon(item, price = currentBid.value)
      startDeleteTimer()
      unregisterInAuctionSearch()
      context become sold
  }

  val sold: Receive = {
    case DeleteTimePassed =>
      auctionEnded()
  }

  private def auctionEnded() = {
    context.parent ! Seller.AuctionEnded(item)
    context stop self
  }

  private def initialize() = {
    startBidTimer()
    context become created
  }

  private def startBidTimer(): Cancellable = startTimer(Config.AuctionBiddingTime, BiddingTimePassed)

  private def startDeleteTimer(): Cancellable = startTimer(Config.AuctionDeleteTime, DeleteTimePassed)

  private def startTimer(time: FiniteDuration, message: Any): Cancellable = {
    import context.dispatcher

    context.system.scheduler.scheduleOnce(time, self, message)
  }
}
