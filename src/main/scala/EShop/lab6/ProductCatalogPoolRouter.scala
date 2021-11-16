package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import EShop.lab5.ProductCatalog.{Item, Items}
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.Await
import scala.language.postfixOps

object ProductCatalogPoolRouter {
  case class Query(brand: String, productKeyWords: List[String])
  case class Response(products: List[Item])
}

trait PCPJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queryFormat = jsonFormat2(ProductCatalogPoolRouter.Query)

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
  implicit val responseFormat = jsonFormat1(ProductCatalogPoolRouter.Response)

}

object ProductCatalogPoolRouterApp extends App {
  new ProductCatalogPoolRouter().start(9000)
}

/** Just to demonstrate how one can build akka-http based server with JsonSupport */
class ProductCatalogPoolRouter extends PCPJsonSupport {
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog"
  )
  val workers = system.systemActorOf(Routers.pool(5)(ProductCatalog(new SearchService())), "productCatalog")

  implicit val timeout: Timeout = Timeout(5 seconds)

  def routes: Route = {
    path("catalog") {
      get {
        parameters("brand".as[String], "words".as[String]) { (brand, words) =>
          val request = workers.ask(ref => ProductCatalog.GetItems(brand, words.split(',').toList, ref))
          onSuccess(request) {
            case Items(items) =>
              complete(ProductCatalogPoolRouter.Response(items))
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
