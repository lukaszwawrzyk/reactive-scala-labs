package auction.actors.fsm

import akka.actor.{ActorRef, FSM, Props}
import auction.Config
import auction.actors.common.Auction._
import auction.actors.common.{Auction, Buyer, Seller}
import auction.actors.fsm.AuctionFsm._
import auction.model._

object AuctionFsm {
  def props(item: Item): Props = Props(new AuctionFsm(item))

  sealed trait State
  private case object Idle extends State
  private case object Created extends State
  private case object Ignored extends State
  private case object Activated extends State
  private case object Sold extends State

  sealed trait Data
  private case object NoBid extends Data
  private case class CurrentBid(value: Money, buyer: ActorRef) extends Data

  private case object BiddingTimePassed
}

class AuctionFsm(val item: Item) extends FSM[State, Data] with Auction {
  startWith(Idle, NoBid)

  when (Idle) {
    case Event(Start, _) =>
      goto (Created)
  }

  when (Created) {
    case Event(Bid(value), _) =>
      goto (Activated) using CurrentBid(value, sender)
    case Event(BiddingTimePassed, _) =>
      goto (Ignored)
  }

  when (Ignored, stateTimeout = Config.AuctionDeleteTime) {
    case Event(Relist, _) =>
      println(s"Auction for ${item.name} was relisted")
      goto (Created)
    case Event(StateTimeout, _) =>
      println(s"Auction for ${item.name} ended without buyer")
      auctionEnded()
  }

  when (Activated) {
    case Event(Bid(newBidValue), CurrentBid(currentBidValue, currentBuyer)) if newBidValue > currentBidValue =>
      println(s"item ${item.name} now has value $newBidValue")
      currentBuyer ! Buyer.OfferOverbid(newBidValue)
      stay using CurrentBid(newBidValue, sender)
    case Event(BiddingTimePassed, CurrentBid(value, buyer)) =>
      goto (Sold)
  }

  when (Sold, stateTimeout = Config.AuctionDeleteTime) {
    case Event(StateTimeout, _) =>
      auctionEnded()
  }

  onTransition {
    case Activated -> Sold =>
      unregisterInAuctionSearch()
      stateData match {
        case CurrentBid(value, buyer) =>
          buyer ! Buyer.AuctionWon(item, price = value)
        case _ => ()
      }
    case _ -> Sold | _ -> Ignored =>
      unregisterInAuctionSearch()
    case _ -> Created =>
      registerInAuctionSearch()
      startBiddingTimer()

  }

  whenUnhandled {
    case _ => stay
  }

  initialize()

  private def startBiddingTimer(): Unit = {
    setTimer("auction bidding time", BiddingTimePassed, Config.AuctionBiddingTime, repeat = false)
  }

  private def auctionEnded(): State = {
    context.parent ! Seller.AuctionEnded(item)
    stop()
  }
}
