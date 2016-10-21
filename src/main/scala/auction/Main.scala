package auction

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import auction.actors.become.AuctionBecome
import auction.actors.common.{AuctionSearch, Buyer, Seller}
import auction.model.{Item, Money}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object Config {
  def auctionsPerItemType() = Random.nextInt(2) + 2
  def buyersPerItemType() = Random.nextInt(20) + 15
  def buyerBudget() = Random.nextInt(1000) + 100
  val MinBidDelta: Money = 10

  val AuctionSearchPath = "/user/auction-search"

  val SearchRetryDelay = FiniteDuration(2, TimeUnit.SECONDS)
  val BuyerBiddingInterval = FiniteDuration(5, TimeUnit.SECONDS)
  val AuctionBiddingTime = FiniteDuration(20, TimeUnit.SECONDS)
  val AuctionDeleteTime = FiniteDuration(5, TimeUnit.SECONDS)
  val RelistAttemptTime = AuctionBiddingTime + AuctionDeleteTime / 2
}

object Main extends App {
//  runWith(AuctionFsm.props)
  runWith(AuctionBecome.props)

  def runWith(propsFactory: Item => Props) = {
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

      val seller = system.actorOf(Seller.props(propsFactory), s"seller-$itemType")
      seller ! Seller.Init(items)

      val buyers = {
        def createBuyer() = system.actorOf(Buyer.props(itemType, Config.buyerBudget()), s"buyer-$itemType-${Random.nextInt}")
        (1 to Config.buyersPerItemType()) map (_ => createBuyer())
      }

      buyers foreach (_ ! Buyer.Start)
    }

    system.actorOf(AuctionSearch.props, "auction-search")

    ('A' to 'E') map (_.toString) foreach startForItem


    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
