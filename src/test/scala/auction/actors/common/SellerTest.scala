package auction.actors.common

import akka.testkit.TestProbe
import auction.actors.common.Seller.AuctionFactory
import auction.model.Item
import helpers.AkkaTest

class SellerTest extends AkkaTest {
  behavior of "Seller"

  it should "create auctions for given items" in new SellerFixture {
    seller ! Seller.Init(List(item1, item2))

    auction1.expectMsg(Auction.Start)
    auction2.expectMsg(Auction.Start)
  }

  it should "stop itself after all auctions ended" in new SellerFixture {
    val watcher = TestProbe()
    watcher watch seller

    seller ! Seller.Init(List(item1, item2))

    seller ! Seller.AuctionEnded(item1)
    seller ! Seller.AuctionEnded(item2)

    watcher.expectTerminated(seller)
  }

  trait SellerFixture {
    val auctionFactory = stub[AuctionFactory]
    val item1 = Item("1")
    val item2 = Item("2")

    val auction1 = TestProbe()
    val auction2 = TestProbe()

    (auctionFactory.create _).when(*, *, item1).returns(auction1.ref)
    (auctionFactory.create _).when(*, *, item2).returns(auction2.ref)

    val seller = system.actorOf(Seller.props(auctionFactory))
  }
}
