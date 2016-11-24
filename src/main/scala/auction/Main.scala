package auction

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import auction.actors.common.Seller.{AuctionFactory, BasicAuctionFactory}
import auction.actors.common.{Buyer, MasterSearch, Seller}
import auction.actors.fsm.AuctionFsm
import auction.model.{Item, Money}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object Config {
  def auctionsPerItemType() = 1
  def buyersPerItemType() = 20
  def buyerBudget() = 1000
  val MinBidDelta: Money = 10

  val ItemNames = 'A' to 'A'

  val AuctionSearchPath = "/user/auction-search"

  val SearchRetryDelay = FiniteDuration(200, TimeUnit.MILLISECONDS)
  val OverbidDelay = FiniteDuration(3, TimeUnit.SECONDS)
  val BuyerBiddingInterval = FiniteDuration(5, TimeUnit.SECONDS)
  val AuctionBiddingTime = FiniteDuration(20, TimeUnit.SECONDS)
  val AuctionDeleteTime = FiniteDuration(5, TimeUnit.SECONDS)
  val RelistAttemptTime = AuctionBiddingTime + AuctionDeleteTime / 2
}

object Main extends App {
  runWith(BasicAuctionFactory(AuctionFsm.props))

  def runWith(auctionFactory: AuctionFactory) = {
    val system = ActorSystem("auction-system")

    def startForItem(itemType: String) = {
      val items = {
        def createItem(number: Int) = {
          val itemName = s"$itemType #$number"
          Item(itemName)
        }

        val itemNumbers = (1 to Config.auctionsPerItemType()).toList
        itemNumbers map createItem
      }

      val seller = system.actorOf(Seller.props(auctionFactory), s"seller-$itemType")
      seller ! Seller.Init(items)

      val buyers = {
        def createBuyer(initBid: => Option[Money] = None) = {
          system.actorOf(Buyer.props(itemType, Config.buyerBudget(), initBid), s"buyer-$itemType-${Random.nextInt}")
        }
        (1 to Config.buyersPerItemType()).map(_ => createBuyer()) // :+ createBuyer(Some(1000))
      }

      buyers foreach (_ ! Buyer.Start)
    }

    system.actorOf(MasterSearch.props, "auction-search")

    Config.ItemNames map (_.toString) foreach startForItem

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
