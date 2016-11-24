package auction.actors.common

import akka.actor.{Actor, Props}
import akka.routing._

object MasterSearch {
  def props(queryRouterLogic: RoutingLogic, numberOfRoutees: Int): Props = {
    Props(new MasterSearch(queryRouterLogic, numberOfRoutees))
  }

  def props: Props = props(RoundRobinRoutingLogic(), 5)
}

class MasterSearch(queryRouterLogic: RoutingLogic, numberOfRoutees: Int) extends Actor {
  val routees = Vector.fill(numberOfRoutees) {
    val routee = context.actorOf(AuctionSearch.props)
    context watch routee
    ActorRefRoutee(routee)
  }

  val commandsRouter = Router(BroadcastRoutingLogic(), routees)
  val queriesRouter = Router(queryRouterLogic, routees)

  def receive = {
    case commandMessage @ (_: AuctionSearch.RegisterAuction | _: AuctionSearch.UnregisterAuction) =>
      commandsRouter.route(commandMessage, sender())
    case queryMessage @ (_: AuctionSearch.Search) =>
      queriesRouter.route(queryMessage, sender())
  }
}
