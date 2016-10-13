package auction.actors.become

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.LoggingReceive
import auction.Config
import auction.actors.become.AuctionBecome._
import auction.actors.common.Auction._
import auction.actors.common.{AuctionManager, Buyer}
import auction.model.Item

import scala.concurrent.duration.FiniteDuration

object AuctionBecome {
  def props(item: Item): Props = Props(new AuctionBecome(item))

  private case class CurrentBid(value: BigDecimal, buyer: ActorRef)

  private case object BiddingTimePassed
  private case object DeleteTimePassed
}

class AuctionBecome(private val item: Item) extends Actor {

  override def receive: Receive = initial

  val initial: Receive = LoggingReceive {
    case Start =>
      initialize()
  }

  val created: Receive = LoggingReceive {
    case BiddingTimePassed =>
      val cancelToken = startDeleteTimer()
      context become ignored(cancelToken)
    case Bid(value) =>
      context become activated(CurrentBid(value, sender))
  }

  def ignored(cancelDeleteTimerToken: Cancellable): Receive = LoggingReceive {
    case DeleteTimePassed =>
      println(s"Auction for ${item.name} ended without buyer")
      auctionEnded()
    case Relist =>
      println(s"Auction for ${item.name} was relisted")
      cancelDeleteTimerToken.cancel()
      initialize()
  }

  def activated(currentBid: CurrentBid): Receive = LoggingReceive {
    case Bid(value) if value > currentBid.value =>
      context become activated(CurrentBid(value, sender))
    case BiddingTimePassed =>
      currentBid.buyer ! Buyer.AuctionWon(item, price = currentBid.value)
      startDeleteTimer()
      context become sold
  }

  val sold: Receive = LoggingReceive {
    case DeleteTimePassed =>
      auctionEnded()
  }

  private def auctionEnded() = {
    context.parent ! AuctionManager.AuctionEnded(item)
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
