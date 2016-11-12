package auction.actors.common


import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, ReceiveTimeout, SupervisorStrategy}
import akka.pattern._
import akka.util.Timeout
import auction.Config
import auction.actors.common.Notifier.AuctionUpdated
import auction.model.{Item, Money}

import scala.concurrent.duration._
import scala.concurrent.TimeoutException
import scala.language.postfixOps

object Notifier {
  case class AuctionUpdated(item: Item, currentBuyer: ActorRef, currentPrice: Money) {
    override val toString = s"${item.name} by ${currentBuyer.path.name} for $currentPrice"
  }
}

class Notifier extends Actor {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: TimeoutException =>
      println("Timeout exception")
      Restart
    case e =>
      println(s"Unexpected error ${e.getClass.getSimpleName} ${e.getMessage}")
      Stop
  }

  override def receive = {
    case auctionUpdated: AuctionUpdated =>
      println(s"Notifier initialized for $auctionUpdated")
      context.actorOf(Props(classOf[NotifierRequest], auctionUpdated))
  }

}

object NotifierRequest {
  case object Ack
}

class NotifierRequest(messageToDeliver: AuctionUpdated) extends Actor {
  import NotifierRequest._

  implicit val timeout = Timeout(5 seconds)
  import context.dispatcher

  override def preStart(): Unit = {
    println(s"Notifier request started for $messageToDeliver")
    context.actorSelection(Config.AuctionPublisherPath).resolveOne() pipeTo self
  }

  override def receive = {
    case publisher: ActorRef =>
      publisher ! messageToDeliver
      context.setReceiveTimeout(timeout.duration)
    case Ack =>
      println(s"Notifier request success for $messageToDeliver")
      context stop self
    case ReceiveTimeout =>
      context.setReceiveTimeout(Duration.Undefined)
      throw new TimeoutException
  }
}