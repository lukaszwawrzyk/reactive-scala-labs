package auction

import java.util.concurrent.TimeUnit._

import akka.actor.{ActorRef, ActorSystem, Props}
import auction.actors.common.Seller.BasicAuctionFactory
import auction.actors.common._
import auction.actors.fsm.AuctionFsm
import auction.model.{Item, Money}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object Config {
  def auctionsPerItemType() = 3

  def buyersPerItemType() = 15

  def buyerBudget() = 1000

  val MinBidDelta: Money = 10

  val ItemNames = 'A' to 'D'

  val AuctionSearchPath = "/user/auction-search"
  val NotifierPath = "/user/notifier"
  val AuctionPublisherPath = "akka.tcp://remote-system@127.0.0.1:2552/user/auction-publisher"

  val SearchRetryDelay = FiniteDuration(200, MILLISECONDS)
  val OverbidDelay = FiniteDuration(3, SECONDS)
  val BuyerBiddingInterval = FiniteDuration(5, SECONDS)
  val AuctionBiddingTime = FiniteDuration(20, SECONDS)
  val AuctionDeleteTime = FiniteDuration(5, SECONDS)
  val RelistAttemptTime = AuctionBiddingTime + AuctionDeleteTime / 2
}

object Main extends App {
  val config = ConfigFactory.load()
  val remoteSystem = startRemoteSystem("remote-system")
  val auctionSystem = startAuctionSystem("auction-system")

  Await.ready(auctionSystem.whenTerminated.flatMap(_ => remoteSystem.whenTerminated), Duration.Inf)

  def startRemoteSystem(name: String): ActorSystem = {
    implicit val system = ActorSystem(name, config.getConfig("serverapp").withFallback(config))
    system.actorOf(Props[AuctionPublisher], "auction-publisher")
    system
  }

  def startAuctionSystem(name: String): ActorSystem = {
    implicit val system = ActorSystem(name, config.getConfig("clientapp").withFallback(config))
    system.actorOf(AuctionSearch.props, "auction-search")
    system.actorOf(Props[Notifier], "notifier")
    Config.ItemNames map (_.toString) foreach startAuctionsWithBuyers
    system
  }

  def startAuctionsWithBuyers(itemType: String)(implicit system: ActorSystem) = {
    val items = createItems(itemType)
    startSeller(itemType, items)
    startBuyers(itemType)
  }

  private def createItems(itemType: String): List[Item] = {
    def createItem(number: Int) = {
      val itemName = s"$itemType #$number"
      Item(itemName)
    }

    val itemNumbers = (1 to Config.auctionsPerItemType()).toList
    itemNumbers map createItem
  }

  private def startSeller(itemType: String, items: List[Item])(implicit system: ActorSystem): Unit = {
    val auctionFactory = BasicAuctionFactory(AuctionFsm.props)
    val seller = system.actorOf(Seller.props(auctionFactory), s"seller-$itemType")

    seller ! Seller.Init(items)
  }

  private def startBuyers(itemType: String)(implicit system: ActorSystem): Unit = {
    createBuyers(itemType) foreach (_ ! Buyer.Start)
  }

  private def createBuyers(itemType: String)(implicit system: ActorSystem): Seq[ActorRef] = {
    def createBuyer(initBid: => Option[Money] = None) = {
      system.actorOf(Buyer.props(itemType, Config.buyerBudget(), initBid), s"buyer-$itemType-${Random.nextInt}")
    }
    (1 to Config.buyersPerItemType()).map(_ => createBuyer())
  }
}
