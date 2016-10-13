package auction.actors.common

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import auction.Config
import auction.actors.become.Auction
import auction.actors.common.AuctionManager._
import auction.model.Item

object AuctionManager {
  case class Init(items: List[Item])
  case class AuctionEnded(item: Item)

  private case object TryRelist

  val props: Props = Props(new AuctionManager)
}

class AuctionManager extends Actor {
  override def receive: Receive = preInit

  val preInit: Receive = LoggingReceive {
    case Init(items) =>
      val auctions = createAndStartAuctions(items)
      val buyers = createBuyers(auctions.values.toList)
      startRelistTimer()
      context become awaitingAuctionsEnd(auctions, buyers)
  }

  def awaitingAuctionsEnd(auctions: Map[Item, ActorRef], buyers: List[ActorRef]): Receive = LoggingReceive {
    case AuctionEnded(item @ Item(itemName)) =>
      val updatedAuctions = auctions - item

      if (updatedAuctions.isEmpty) {
        buyers foreach (_ ! Buyer.Stop)
        context.system.terminate()
        println("Last auction ended, terminating...")
      } else {
        context become awaitingAuctionsEnd(updatedAuctions, buyers)
      }
    case TryRelist =>
      val auctionsToAttemptRelist = auctions.values.filter(_ => scala.util.Random.nextBoolean())
      auctionsToAttemptRelist foreach (_ ! Auction.Relist)
  }

  private def startRelistTimer() = {
    context.system.scheduler.scheduleOnce(Config.RelistAttemptTime, self, TryRelist)(context.dispatcher)
  }

  private def createAndStartAuctions(items: List[Item]): Map[Item, ActorRef] = {
    val auctions = createAuctions(items)

    val auctionActors = auctions.values
    auctionActors foreach (_ ! Auction.Start)

    auctions
  }

  def createAuctions(items: List[Item]): Map[Item, ActorRef] = {
    items.zipWithIndex.map {
      case (item, index) =>
        val actorName = s"auction-${index + 1}"
        val auctionActor = context.actorOf(Auction.props(item), actorName)
        item -> auctionActor
    }.toMap
  }

  private def createBuyers(auctionActors: List[ActorRef]): List[ActorRef] = {
    val buyerIndices = (1 to Config.BuyersCount).toList
    buyerIndices map { id =>
      val actorName = s"buyer-$id"
      context.actorOf(Buyer.props(auctionActors), actorName)
    }
  }
}
