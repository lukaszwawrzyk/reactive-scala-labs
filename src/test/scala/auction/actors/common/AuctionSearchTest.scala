package auction.actors.common

import akka.actor.ActorRef
import akka.testkit.TestProbe
import auction.model.Item
import helpers.AkkaTest

class AuctionSearchTest extends AkkaTest {

  behavior of "AuctionSearch"

  it should "return empty result when empty" in new AuctionSearchContext {
    searchAuctions("any")
    expectNoAuctions()
  }


  it should "return matching auctions" in new AuctionSearchContext {
    val actor111 = dummyActor()
    val actor11 = dummyActor()
    val actor1111 = dummyActor()
    val actor12222 = dummyActor()
    val actor2225 = dummyActor()
    registerAuction(actor111, "abc 111")
    registerAuction(actor11, "xyz 11")
    registerAuction(actor1111, "xyz 1111")
    registerAuction(actor12222, "axx 12222")
    registerAuction(actor2225, "ayz 2225")

    searchAuctions("11")
    expectAuctions(actor111, actor11, actor1111)

    searchAuctions("1")
    expectAuctions(actor111, actor11, actor1111, actor12222)

    searchAuctions("1111")
    expectAuctions(actor1111)

    searchAuctions("222")
    expectAuctions(actor12222, actor2225)
  }

  it should "unregister auctions" in new AuctionSearchContext {
    val actor1 = dummyActor()
    registerAuction(actor1, "1")
    unregisterAuction(actor1)
    searchAuctions("1")
    expectNoAuctions()
  }

  trait AuctionSearchContext {
    val auctionSearch = system.actorOf(AuctionSearch.props)

    def registerAuction(auctionActor: ActorRef, itemName: String) = {
      auctionSearch ! AuctionSearch.RegisterAuction(Item(itemName), auctionActor)
    }

    def unregisterAuction(auctionActor: ActorRef) = {
      auctionSearch ! AuctionSearch.UnregisterAuction(auctionActor)
    }

    def searchAuctions(query: String) = {
      auctionSearch ! AuctionSearch.Search(query)
    }

    def expectAuctions(auctions: ActorRef*) = {
      expectMsg(AuctionSearch.MatchingAuctions(auctions.toSet))
    }

    def expectNoAuctions() = {
      expectAuctions()
    }

    def dummyActor() = TestProbe().ref
  }

}
