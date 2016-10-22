package auction.actors.common

import akka.actor.{Actor, ActorRef, Props}
import auction.Config
import auction.actors.common.Buyer._
import auction.model._

import scala.language.implicitConversions
import scala.util.Random

object Buyer {
  case class OfferOverbid(offerValue: Money)
  case class AuctionWon(item: Item, price: Money)
  case object Start

  def props(auctionNameKeyword: String, budget: Money): Props = Props(new Buyer(auctionNameKeyword, budget))

  implicit def func0ToRunnable(f: () => Unit): Runnable = new Runnable { override def run(): Unit = f() }
}

class Buyer(
  auctionNameKeyword: String,
  budget: Money
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
        log(s"retrying with $nextBid after being overbid (limit $budget)")
        auction ! Auction.Bid(nextBid)
      } else {
        log("out of money")
        context stop self
      }
    case AuctionWon(Item(itemName), price) =>
      log(s"bought $itemName for $price")
      context stop self
  }

  private def sendSearchQuery(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.Search(auctionNameKeyword)
  }

  private def randomBid() = {
    val bidValue = Random.nextInt(budget.toInt / 2)
    Auction.Bid(bidValue)
  }

  private def log(message: String): Unit = {
    println(s"[${context.self.path.name}] $message")
  }
}
