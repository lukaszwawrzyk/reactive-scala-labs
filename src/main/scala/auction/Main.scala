package auction

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import auction.actors.become.AuctionBecome
import auction.actors.common.Seller
import auction.actors.fsm.AuctionFsm
import auction.model.Item

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}

object Config {
  val AuctionCount = 20
  val BuyersCount = 5

  val BuyerBiddingInterval = FiniteDuration(5, TimeUnit.SECONDS)
  val AuctionBiddingTime = FiniteDuration(20, TimeUnit.SECONDS)
  val AuctionDeleteTime = FiniteDuration(5, TimeUnit.SECONDS)
  val RelistAttemptTime = AuctionBiddingTime + AuctionDeleteTime / 2
}

object Main extends App {
  runWith(AuctionFsm.props)
  runWith(AuctionBecome.props)

  def runWith(propsFactory: Item => Props) = {
    val system = ActorSystem("auction-system")

    val manager = system.actorOf(Seller.props(propsFactory), "auction-manager")
    val items = {
      def createItem(number: Int) = {
        val name = s"Item #$number"
        Item(name)
      }

      val itemNumbers = (1 to Config.AuctionCount).toList
      itemNumbers map createItem
    }

    manager ! Seller.Init(items)

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
