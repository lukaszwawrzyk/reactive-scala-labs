package toggle

import akka.actor.{Actor, ActorRef, Props}
import akka.persistence._
import auction.Config

object PersistentToggle {
  sealed trait State
  case object On extends State
  case object Off extends State

  case class ToggleEvent(state: State)

  case object Toggle
  case object Stop
}

class PersistentToggle extends PersistentActor {
  import PersistentToggle._

  override def persistenceId = "persistent-toggle-id-1"

  var counter = 0

  def updateState(event: ToggleEvent): Unit = {
    counter += 1
    context.become {
      event.state match {
        case On => on
        case Off => off
      }
    }
  }

  val on: Receive = {
    case Toggle => persist(ToggleEvent(Off)) { event =>
      updateState(event)
      sender ! "ack"
    }
    case Stop =>
      context stop self
  }

  val off: Receive = {
    case Toggle => persist(ToggleEvent(On)) { event =>
      updateState(event)
      sender ! "ack"
    }
    case Stop =>
      context stop self
  }

  def receiveCommand = on

  val receiveRecover: Receive = {
    case event: ToggleEvent => updateState(event)
    case RecoveryCompleted =>
      println("loaded with " + counter)
      context.parent ! "ready"
  }
}

class Requester extends Actor {
  var toggleActor: ActorRef = _
  var counter = 0

  var startTime: Long = 0

  override def receive: Receive = {
    case "start" =>
      startTime = System.nanoTime()
      toggleActor = context.actorOf(Props(new PersistentToggle), "toggle")
    case "ready" =>
      val time = (System.nanoTime() - startTime) / 1e9
      println(s"loaded in $time s")

      startTime = System.nanoTime()
      for (_ <- 1 to Config.ToggleCount) {
        toggleActor ! PersistentToggle.Toggle
      }
    case "ack" =>
      counter += 1
      if (counter == Config.ToggleCount) {
        toggleActor ! PersistentToggle.Stop
        val time = (System.nanoTime() - startTime) / 1e9
        println(s"stored in $time s")
        context.system.terminate()
      }
  }
}