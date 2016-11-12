package auction.actors.common

import akka.actor.Actor
import auction.actors.common.Notifier.AuctionUpdated

import scala.util.Random

class AuctionPublisher extends Actor {
  override def receive = {
    case auctionUpdated: AuctionUpdated =>
      if (Random.nextBoolean()) {
        println(s"Publisher got $auctionUpdated")
        sender() ! NotifierRequest.Ack
      } else {
        println(s"Publisher rejected $auctionUpdated")
      }
  }
}

