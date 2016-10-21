package auction.actors.common

import akka.actor.Actor
import auction.Config
import auction.model.{Item, Money}

object Auction {
  case object Start
  case object Relist
  case class Bid(value: Money)
}

trait Auction { this: Actor =>
  def item: Item

  protected def registerInAuctionSearch(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.RegisterAuction(item, self)
  }

  protected def unregisterInAuctionSearch(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.UnregisterAuction(self)
  }
}