package auction.actors.common

import akka.actor.{Actor, ActorRef, Props}
import auction.Config
import auction.actors.common.Buyer._
import auction.actors.fsm.AuctionFsm
import auction.model._

import scala.language.implicitConversions
import scala.util.Random

object Buyer {
  case class OfferOverbid(offerValue: Money)
  case class AuctionWon(item: Item, price: Money)
  case object Start

  private case class BidNow(value: Money)

  def props(auctionNameKeyword: String, budget: Money, initBid: Option[Money]): Props = Props(new Buyer(auctionNameKeyword, budget, initBid))

  implicit def func0ToRunnable(f: () => Unit): Runnable = new Runnable { override def run(): Unit = f() }
}

class Buyer(
  auctionNameKeyword: String,
  budget: Money,
  initBid: Option[Money]
) extends Actor {

  override def receive: Receive = idle

  lazy val idle: Receive = {
    case Start =>
      sendSearchQuery()
      context become awaitingAuctions
  }

  lazy val awaitingAuctions: Receive = {
    case AuctionSearch.MatchingAuctions(auctions) =>
      Random.shuffle(auctions.toSeq).headOption.fold[Unit]{
        context.system.scheduler.scheduleOnce(Config.SearchRetryDelay, () => sendSearchQuery())(context.dispatcher)
      } { auction =>
        auction ! randomBid()
        context become bidding(auction)
      }
  }

  def bidding(auction: ActorRef): Receive = {
    case OfferOverbid(overbidValue) =>
      val nextBid = overbidValue + Config.MinBidDelta
      if (nextBid <= budget) {
        log(s"will retry with $nextBid after being overbid (limit $budget)")
        context.system.scheduler.scheduleOnce(Config.OverbidDelay, self, BidNow(nextBid))(context.dispatcher)
      } else {
        log("out of money")
        context stop self
      }
    case BidNow(value) =>
      auction ! AuctionFsm.MakeBid(value)
    case AuctionWon(Item(itemName), price) =>
      log(s"bought $itemName for $price")
      context stop self
  }

  private def sendSearchQuery(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.Search(auctionNameKeyword)
  }

  private def randomBid() = {
    val bidValue = initBid.getOrElse(BigDecimal(Random.nextInt(budget.toInt / 2)))
    AuctionFsm.MakeBid(bidValue)
  }

  private def log(message: String): Unit = {
    println(s"[${context.self.path.name}] $message")
  }
}
