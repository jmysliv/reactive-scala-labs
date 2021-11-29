package EShop.lab6

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.net.URI
import java.util.zip.GZIPInputStream
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.Random

object CounterWorker {
  val CounterWorkerServiceKey = ServiceKey[Query]("CounterWorker")

  sealed trait Query
  case class RequestReceived(instanceId: String) extends Query
  case class GetStats(replyTo: ActorRef[Ack])    extends Query

  sealed trait Ack
  case class Stats(counterMap: Map[String, Int]) extends Ack

  def apply(): Behavior[Query] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.register(CounterWorkerServiceKey, context.self)
      listen(Map.empty[String, Int])
    }

  def listen(counterMap: Map[String, Int]): Behavior[Query] =
    Behaviors.receiveMessage {
      case RequestReceived(instanceId) =>
        if (counterMap.contains(instanceId))
          listen(counterMap + (instanceId -> (counterMap(instanceId) + 1)))
        else
          listen(counterMap + (instanceId -> 1))
      case GetStats(replyTo) =>
        replyTo ! Stats(counterMap)
        Behaviors.same

    }
}

object CounterWorkerApp extends App {

  private val config = ConfigFactory.load()

  private val counterWorkerSystem = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config.getConfig("counter").withFallback(config)
  )

  counterWorkerSystem.systemActorOf(
    CounterWorker(),
    "CounterWorker"
  )

  Await.result(counterWorkerSystem.whenTerminated, Duration.Inf)
}
