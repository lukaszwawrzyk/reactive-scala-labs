package auction.actors.fsm

import akka.actor.{ActorRef, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM._
import auction.Config
import auction.actors.common.{AuctionSearch, Buyer, Seller}
import auction.actors.fsm.AuctionFsm._
import auction.model._

import scala.concurrent.duration._
import scala.reflect.ClassTag

object AuctionFsm {
  def props(item: Item): Props = Props(new AuctionFsm(item))

  case class Bid(value: Money, bidder: ActorRef)

  sealed trait AuctionCommand
  case object StartAuction extends AuctionCommand
  case object RelistAuction extends AuctionCommand
  case class MakeBid(value: Money) extends AuctionCommand

  sealed abstract class AuctionState(val identifier: String) extends FSMState
  case object Idle extends AuctionState("idle")
  case object Created extends AuctionState("created")
  case object Ignored extends AuctionState("ignored")
  case object Activated extends AuctionState("activated")
  case object Sold extends AuctionState("sold")

  sealed trait AuctionData {
    def auctionStart: Option[EpochMillis]
    def auctionEnd: Option[EpochMillis] = auctionStart map (_ + Config.AuctionBiddingTime.toMillis)
  }
  case class NoBid(auctionStart: Option[EpochMillis]) extends AuctionData
  case class CurrentBid(bid: Bid, auctionStart: Option[EpochMillis]) extends AuctionData

  sealed trait AuctionEvent
  case class AuctionStarted(startTime: EpochMillis) extends AuctionEvent
  case class AuctionEnded(bid: Option[Bid]) extends AuctionEvent
  case object AuctionStarted { def now() = AuctionStarted(System.currentTimeMillis()) }
  case class BidderBid(bid: Bid) extends AuctionEvent

  private case object BiddingTimePassed
}

class AuctionFsm(val item: Item) extends PersistentFSM[AuctionState, AuctionData, AuctionEvent] {
  override def domainEventClassTag = implicitly[ClassTag[AuctionEvent]]

  override def persistenceId: String = s"auction: $item"

  startWith(Idle, NoBid(None))

  when (Idle) {
    case Event(StartAuction, _) =>
      goto (Created) applying AuctionStarted.now()
  }

  when (Created) {
    case Event(MakeBid(value), _) =>
      goto (Activated) applying BidderBid(Bid(value, sender))
    case Event(BiddingTimePassed, _) =>
      goto (Ignored) applying AuctionEnded(bid = None)
  }

  when (Ignored, stateTimeout = Config.AuctionDeleteTime) {
    case Event(RelistAuction, _) =>
      println(s"Auction for ${item.name} was relisted")
      goto (Created) applying AuctionStarted.now()
    case Event(StateTimeout, _) =>
      println(s"Auction for ${item.name} ended without buyer")
      auctionEnded()
  }

  when (Activated) {
    case Event(MakeBid(newBidValue), CurrentBid(Bid(currentBidValue, currentBuyer), _)) if newBidValue > currentBidValue =>
      println(s"item ${item.name} now has value $newBidValue")
      stay applying BidderBid(Bid(newBidValue, sender)) andThen {
        case _ =>
          currentBuyer ! Buyer.OfferOverbid(newBidValue)
      }
    case Event(BiddingTimePassed, CurrentBid(bid, _)) =>
      goto (Sold) applying AuctionEnded(bid = Some(bid))
  }

  when (Sold, stateTimeout = Config.AuctionDeleteTime) {
    case Event(StateTimeout, _) =>
      auctionEnded()
  }

  onTransition {
    case Activated -> Sold =>
      unregisterInAuctionSearch()
      stateData match {
        case CurrentBid(Bid(value, buyer), _) =>
          buyer ! Buyer.AuctionWon(item, price = value)
        case _ => ()
      }
    case _ -> Sold | _ -> Ignored =>
      unregisterInAuctionSearch()
    case _ -> Created =>
      registerInAuctionSearch()
  }

  def applyEvent(domainEvent: AuctionEvent, currentData: AuctionData): AuctionData = {
    domainEvent match {
      case AuctionStarted(startTime) =>
        startBiddingTimer(Config.AuctionBiddingTime)
        NoBid(Some(startTime))
      case BidderBid(bid) =>
        CurrentBid(bid, currentData.auctionStart)
      case AuctionEnded(maybeBid) =>
        maybeBid.map(CurrentBid(_, auctionStart = None)).getOrElse(NoBid(None))
    }
  }

  whenUnhandled {
    case _ => stay
  }

  override def onRecoveryCompleted(): Unit = {
    println("RECOVERY COMPLETED")
    stateData.auctionEnd
      .map(endTime => endTime - System.currentTimeMillis())
      .filter(_ >= 0)
      .foreach { auctionTimeLeft =>
        registerInAuctionSearch()
        startBiddingTimer(auctionTimeLeft.millis)
      }
  }

  private def startBiddingTimer(timeout: FiniteDuration): Unit = {
    println(s"START TIMER FOR ${timeout.toSeconds}")
    setTimer("auction bidding time", BiddingTimePassed, timeout, repeat = false)
  }

  private def auctionEnded(): State = {
    context.parent ! Seller.AuctionEnded(item)
    stop
  }

  private def registerInAuctionSearch(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.RegisterAuction(item, self)
  }

  private def unregisterInAuctionSearch(): Unit = {
    context.actorSelection(Config.AuctionSearchPath) ! AuctionSearch.UnregisterAuction(self)
  }
}
