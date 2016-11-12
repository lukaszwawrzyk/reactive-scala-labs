package auction.actors.common

import akka.actor.{Actor, ActorRef}
import auction.Config
import auction.actors.common.Notifier.AuctionUpdated
import auction.model.{Item, Money}

object Notifier {
  case class AuctionUpdated(item: Item, currentBuyer: ActorRef, currentPrice: Money)
}

class Notifier extends Actor {
  override def receive = {
    case auctionUpdated: AuctionUpdated =>
      context.actorSelection(Config.AuctionPublisherPath) ! auctionUpdated
  }
}

class AuctionPublisher extends Actor {
  override def receive = {
    case auctionUpdated: AuctionUpdated =>
      println(s"Publisher got $auctionUpdated")
  }
}
