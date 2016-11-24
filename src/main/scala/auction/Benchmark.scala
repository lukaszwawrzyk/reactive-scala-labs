package auction

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing._
import auction.BenchmarkActor._
import auction.actors.common.{AuctionSearch, MasterSearch}
import auction.model.Item

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

/*
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:  20 time:  9,200  8,208  9,488 avg:  8,965
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:  10 time:  6,496  5,474  7,824 avg:  6,598
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   6 time:  4,042  6,575  7,223 avg:  5,946
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   5 time:  7,747  3,843  7,569 avg:  6,386
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   4 time:  3,060  7,806  5,758 avg:  5,541
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   3 time:  3,681  4,047  4,402 avg:  4,043
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   2 time:  5,086  6,452  6,380 avg:  5,973
Routing: ScatterGatherFirstCompletedRoutingLogic       Routees:   1 time:  3,775  4,963  4,038 avg:  4,259
Routing: RoundRobinRoutingLogic                        Routees:  20 time:  1,674  1,830  1,770 avg:  1,758
Routing: RoundRobinRoutingLogic                        Routees:  10 time:  1,948  0,577  1,859 avg:  1,461
Routing: RoundRobinRoutingLogic                        Routees:   6 time:  1,939  1,932  1,953 avg:  1,941
Routing: RoundRobinRoutingLogic                        Routees:   5 time:  1,660  2,064  1,516 avg:  1,747
Routing: RoundRobinRoutingLogic                        Routees:   4 time:  2,226  1,784  2,270 avg:  2,093
Routing: RoundRobinRoutingLogic                        Routees:   3 time:  2,613  2,681  2,788 avg:  2,694
Routing: RoundRobinRoutingLogic                        Routees:   2 time:  3,180  3,360  1,277 avg:  2,606
Routing: RoundRobinRoutingLogic                        Routees:   1 time:  5,097  4,739  5,438 avg:  5,091
Routing: RandomRoutingLogic                            Routees:  20 time:  0,465  1,196  1,740 avg:  1,133
Routing: RandomRoutingLogic                            Routees:  10 time:  2,025  1,554  2,302 avg:  1,961
Routing: RandomRoutingLogic                            Routees:   6 time:  0,737  2,171  0,827 avg:  1,245
Routing: RandomRoutingLogic                            Routees:   5 time:  2,021  1,440  1,942 avg:  1,801
Routing: RandomRoutingLogic                            Routees:   4 time:  2,170  2,476  2,453 avg:  2,366
Routing: RandomRoutingLogic                            Routees:   3 time:  1,500  2,975  3,649 avg:  2,708
Routing: RandomRoutingLogic                            Routees:   2 time:  4,240  4,197  2,608 avg:  3,682
Routing: RandomRoutingLogic                            Routees:   1 time:  7,584  8,100  7,092 avg:  7,592
Routing: SmallestMailboxRoutingLogic                   Routees:  20 time:  1,594  2,160  1,851 avg:  1,868
Routing: SmallestMailboxRoutingLogic                   Routees:  10 time:  1,338  2,316  0,809 avg:  1,488
Routing: SmallestMailboxRoutingLogic                   Routees:   6 time:  2,488  1,956  2,553 avg:  2,332
Routing: SmallestMailboxRoutingLogic                   Routees:   5 time:  3,104  3,336  3,278 avg:  3,239
Routing: SmallestMailboxRoutingLogic                   Routees:   4 time:  1,507  2,895  2,225 avg:  2,209
Routing: SmallestMailboxRoutingLogic                   Routees:   3 time:  2,818  2,977  0,925 avg:  2,240
Routing: SmallestMailboxRoutingLogic                   Routees:   2 time:  3,604  4,127  2,768 avg:  3,500
Routing: SmallestMailboxRoutingLogic                   Routees:   1 time:  5,677  5,460  7,282 avg:  6,140
Routing: BroadcastRoutingLogic                         Routees:  20 time:  1,317  1,729  1,724 avg:  1,590
Routing: BroadcastRoutingLogic                         Routees:  10 time:  1,264  2,669  2,008 avg:  1,980
Routing: BroadcastRoutingLogic                         Routees:   6 time:  2,839  2,446  2,642 avg:  2,642
Routing: BroadcastRoutingLogic                         Routees:   5 time:  1,452  2,806  1,603 avg:  1,954
Routing: BroadcastRoutingLogic                         Routees:   4 time:  3,273  3,092  3,289 avg:  3,218
Routing: BroadcastRoutingLogic                         Routees:   3 time:  3,838  2,100  3,707 avg:  3,215
Routing: BroadcastRoutingLogic                         Routees:   2 time:  4,520  3,493  3,880 avg:  3,964
Routing: BroadcastRoutingLogic                         Routees:   1 time:  8,133  6,901  7,109 avg:  7,381
*/


object Benchmark extends App {
  val logics = Seq(
    ScatterGatherFirstCompletedRoutingLogic(10.minutes),
    RoundRobinRoutingLogic(),
    RandomRoutingLogic(),
    SmallestMailboxRoutingLogic(),
    BroadcastRoutingLogic()
  )

  val routeesNumbers = Seq(1, 2, 3, 4, 5, 6, 10, 20).reverse

  for {
    logic <- logics
    routees <- routeesNumbers
  } {
    val currentTest = () => test(logic, routees)
    val testResults = (1 to 3).map(_ => currentTest())
    val averageResult = testResults.sum / testResults.size

    val logicName = logic.getClass.getSimpleName
    val message = "Routing: %-45s Routees: %3d time: %6.3f %6.3f %6.3f avg: %6.3f".format(
      logicName, routees, testResults(0), testResults(1), testResults(2), averageResult
    )
    println(message)
  }

  def test(logic: RoutingLogic, searches: Int): Double = {
    val queryTime = Promise[Double]()
    val system = ActorSystem()
    val masterAuction = system.actorOf(MasterSearch.props(logic, searches))
    val benchmark = system.actorOf(Props(new BenchmarkActor(masterAuction, queryTime)))
    benchmark ! Start

    val totalTime = for {
      time <- queryTime.future
      _    <- system.whenTerminated
    } yield time

    Await.result(totalTime, 1.hour)
  }

}

object BenchmarkActor {
  case object Start

  val AuctionsToRegister = 10000
  val QueriesToPerform = 5000
}

class BenchmarkActor(masterSearch: ActorRef, queryTimePromise: Promise[Double]) extends Actor {

  def receive = preInit

  val preInit: Receive = {
    case Start =>
      registerAuctions()
      context become awaitingRegistrationCompleted(confirmations = 0)
  }

  def awaitingRegistrationCompleted(confirmations: Int): Receive = {
    case AuctionSearch.Ack =>
      if (confirmations + 1 == AuctionsToRegister) {
        queryAuctions()
        val now = System.nanoTime()
        context become awaitingQueryingCompleted(results = 0, startTime = now)
      } else {
        context become awaitingRegistrationCompleted(confirmations + 1)
      }
  }

  def awaitingQueryingCompleted(results: Int, startTime: Long): Receive = {
    case AuctionSearch.MatchingAuctions(_) =>
      if (results + 1 == QueriesToPerform) {
        val endTime = System.nanoTime()
        val totalSeconds = (endTime - startTime) / 1e9
        queryTimePromise.complete(Success(totalSeconds))
        context.system.terminate()
      } else {
        context become awaitingQueryingCompleted(results + 1, startTime)
      }
  }

  def registerAuctions() = {
    (1 to AuctionsToRegister) foreach { auctionIndex =>
      val auctionActor = context.actorOf(Props[DummyActor])
      masterSearch ! AuctionSearch.RegisterAuction(Item(s"auction-$auctionIndex"), auctionActor)
    }
  }

  def queryAuctions() = {
    (1 to QueriesToPerform).foreach { queryIndex =>
      val query = scala.util.Random.nextInt(100).toString
      masterSearch ! AuctionSearch.Search(query)
    }
  }
}

class DummyActor extends Actor {
  def receive: Receive = {
    case _ =>
  }
}