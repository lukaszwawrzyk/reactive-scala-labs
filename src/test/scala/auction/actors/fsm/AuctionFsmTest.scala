package auction.actors.fsm

import akka.actor.FSM
import akka.testkit.{TestFSMRef, TestProbe}
import auction.actors.common.{Auction, Buyer, Seller}
import auction.actors.fsm.AuctionFsm.{Created, NoBid, Sold}
import auction.model.Item
import helpers.AkkaTest

class AuctionFsmTest extends AkkaTest {
  behavior of "AuctionFsm"

  it should "notify buyer when overbid" in new AuctionFixture {
    auction.setState(Created, NoBid)

    buyer1.send(auction, Auction.Bid(10))

    buyer2.send(auction, Auction.Bid(30))

    buyer1.expectMsg(Buyer.OfferOverbid(30))
  }

  it should "notify parent when auction ends" in new AuctionFixture {
    auction.setState(Sold)
    auction ! FSM.StateTimeout

    parent.expectMsg(Seller.AuctionEnded(item))
  }

  trait AuctionFixture {
    val item: Item = Item("1")
    val parent = TestProbe()
    val auction = TestFSMRef(new AuctionFsm(item), parent.ref)
    val buyer1 = TestProbe()
    val buyer2 = TestProbe()
  }
}
