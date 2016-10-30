package auction.actors.common

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingReceive
import auction.Config
import auction.actors.common.Seller._
import auction.actors.fsm.AuctionFsm
import auction.model.Item

import scala.util.Random

object Seller {
  case class Init(items: List[Item])
  case class AuctionEnded(item: Item)

  private case object TryRelist

  def props(auctionFactory: AuctionFactory): Props = Props(new Seller(auctionFactory))

  trait AuctionFactory {
    def create(actorRefFactory: ActorRefFactory, name: String, item: Item): ActorRef
  }

  case class BasicAuctionFactory(propsFactory: Item => Props) extends AuctionFactory {
    override def create(actorRefFactory: ActorRefFactory, name: String, item: Item): ActorRef = {
      actorRefFactory.actorOf(propsFactory(item), name)
    }
  }
}

class Seller(private val auctionFactory: AuctionFactory) extends Actor {
  override def receive: Receive = preInit

  val preInit: Receive = LoggingReceive {
    case Init(items) =>
      val auctions = createAndStartAuctions(items)
      startRelistTimer()
      context become awaitingAuctionsEnd(auctions)
  }

  def awaitingAuctionsEnd(auctions: Map[Item, ActorRef]): Receive = LoggingReceive {
    case AuctionEnded(item @ Item(itemName)) =>
      val updatedAuctions = auctions - item

      if (updatedAuctions.isEmpty) {
        context stop self
      } else {
        context become awaitingAuctionsEnd(updatedAuctions)
      }
    case TryRelist =>
      val auctionsToAttemptRelist = auctions.values.filter(_ => scala.util.Random.nextBoolean())
      auctionsToAttemptRelist foreach (_ ! AuctionFsm.RelistAuction)
  }

  private def startRelistTimer() = {
    context.system.scheduler.scheduleOnce(Config.RelistAttemptTime, self, TryRelist)(context.dispatcher)
  }

  private def createAndStartAuctions(items: List[Item]): Map[Item, ActorRef] = {
    val auctions = createAuctions(items)

    val auctionActors = auctions.values
    auctionActors foreach (_ ! AuctionFsm.StartAuction)

    auctions
  }

  def createAuctions(items: List[Item]): Map[Item, ActorRef] = {
    items.map { item =>
      val actorName = s"auction-${Random.nextInt()}"
      val auctionActor = auctionFactory.create(context, actorName, item)
      item -> auctionActor
    }.toMap
  }
}
