package auction

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import auction.actors.common.AuctionManager
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
  val system = ActorSystem("auction-system")

  val manager = system.actorOf(AuctionManager.props, "auction-manager")
  val items = {
    def createItem(number: Int) = {
      val name = s"Item #$number"
      Item(name)
    }

    val itemNumbers = (1 to Config.AuctionCount).toList
    itemNumbers map createItem
  }

  manager ! AuctionManager.Init(items)

  Await.ready(system.whenTerminated, Duration.Inf)
}
