package auction

import akka.actor.{ActorSystem, Props}
import toggle.Requester

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Config {
  val ToggleCount = 5000
}

import Test._

object Main extends App {
  (1 to 5) foreach (_ => println(s"all in: ${timed(testOnce())}"))

  private def testOnce(): Unit = {
    val system = ActorSystem()
    system.actorOf(Props(new Requester)) ! "start"
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object Test {
  def timed(block: => Unit) = {
    val start = System.nanoTime()
    block
    val end = System.nanoTime()

    val duration = (end - start) / 1e9

    duration
  }
}
