package EShop.lab6

import EShop.lab5.ProductCatalog
import EShop.lab5.ProductCatalog.{Item, Items, ProductCatalogServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.{ActorSystem}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.Await
import scala.language.postfixOps

object ProductCatalogServerNode {
  case class Query(brand: String, productKeyWords: List[String])
  case class Response(products: List[Item])
}

trait PCSNJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queryFormat = jsonFormat2(ProductCatalogServerNode.Query)

  //custom formatter just for example
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat     = jsonFormat5(Item)
  implicit val responseFormat = jsonFormat1(ProductCatalogServerNode.Response)

}

object ProductCatalogServerNodeApp extends App {
  new ProductCatalogServerNode(args(0).toInt).start(9000 + args(0).toInt)
}

/** Just to demonstrate how one can build akka-http based server with JsonSupport */
class ProductCatalogServerNode(id: Int) extends PCSNJsonSupport {
  private val config = ConfigFactory.load()
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config.getConfig(s"cluster$id").withFallback(config)
  )
  val workers = system.systemActorOf(Routers.group(ProductCatalogServiceKey), "clusterWorkerRouter")

  implicit val timeout: Timeout = Timeout(5 seconds)

  def routes: Route = {
    path("catalog") {
      get {
        parameters("brand".as[String], "words".as[String]) { (brand, words) =>
          val request = workers.ask(ref => ProductCatalog.GetItems(brand, words.split(',').toList, ref))
          onSuccess(request) {
            case Items(items) =>
              complete(ProductCatalogServerNode.Response(items))
          }
        }
      }
    }
  }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.result(system.whenTerminated, Duration.Inf)
  }

}
