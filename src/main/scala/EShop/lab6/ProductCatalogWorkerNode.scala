package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object ProductCatalogWorkerNode extends App {

  private val config = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
      .getConfig(Try(args(0)).getOrElse("seed-node1"))
      .withFallback(config)
  )

  for (i <- 0 to 3)
    system.systemActorOf(
      ProductCatalog(new SearchService()),
      s"productcatalog$i"
    )

  Await.result(system.whenTerminated, Duration.Inf)
}
