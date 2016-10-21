package auction.actors.common

import akka.actor.{Actor, ActorRef, Props}
import auction.actors.common.AuctionSearch.{MatchingAuctions, RegisterAuction, Search, UnregisterAuction}
import auction.model.Item

object AuctionSearch {
  def props = Props(new AuctionSearch)

  case class Search(pattern: String)
  case class RegisterAuction(item: Item, actor: ActorRef)
  case class UnregisterAuction(actor: ActorRef)
  case class MatchingAuctions(auctions: Seq[ActorRef])
}

class AuctionSearch extends Actor {
  override def receive: Receive = keepingAuctions(Map.empty)

  def keepingAuctions(auctions: Map[ActorRef, Item]): Receive = {
    case Search(pattern) =>
      val actors = auctions.filter {
        case (actor, item) => item.name contains pattern
      }.keys.toSeq

      sender ! MatchingAuctions(actors)
    case RegisterAuction(item, actor) =>
      println(s"registered auction for item ${item.name}")
      context become keepingAuctions(auctions + (actor -> item))
    case UnregisterAuction(actor) =>
      context become keepingAuctions(auctions - actor)
  }
}
