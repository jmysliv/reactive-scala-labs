package EShop.lab5

import EShop.lab5.ProductCatalog.{Ack, Item, Items, ProductCatalogServiceKey, Query}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
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

object ProductCatalogHttpServer {
  case class Query(brand: String, productKeyWords: List[String])
  case class Response(products: List[Item])
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queryFormat = jsonFormat2(ProductCatalogHttpServer.Query)

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
  implicit val responseFormat = jsonFormat1(ProductCatalogHttpServer.Response)

}

object ProductCatalogHttpServerApp extends App {
  new ProductCatalogHttpServer().start(9000)
}

/** Just to demonstrate how one can build akka-http based server with JsonSupport */
class ProductCatalogHttpServer extends JsonSupport {
  private val config = ConfigFactory.load()
  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog",
    config.getConfig("productcatalogserver").withFallback(config)
  )
  val helperActor               = system.systemActorOf(findProductCatalog(), "helperActor")
  implicit val timeout: Timeout = Timeout(5 seconds)

  def routes: Route = {
    path("catalog") {
      get {
        parameters("brand".as[String], "words".as[String]) { (brand, words) =>
          val request = helperActor.ask(ref => GetItems(brand, words.split(',').toList, ref))
          onSuccess(request) {
            case Items(items) =>
              complete(ProductCatalogHttpServer.Response(items))
          }
        }
      }
    }
  }

  sealed trait Command
  case class ListingResponse(listing: Receptionist.Listing)                                extends Command
  case class GetItems(brand: String, productKeyWords: List[String], sender: ActorRef[Ack]) extends Command

  def findProductCatalog(): Behavior[Command] =
    Behaviors.setup { context =>
      val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
      context.system.receptionist ! Receptionist.Subscribe(ProductCatalogServiceKey, listingResponseAdapter)
      Behaviors.receiveMessage {
        case ListingResponse(ProductCatalogServiceKey.Listing(listing)) =>
          println("*************************")
          println(listing.size)
          waitingForRequest(listing)
      }
    }

  def waitingForRequest(listing: Set[ActorRef[Query]]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetItems(brand, productKeyWords, sender) =>
        listing.foreach(pc => pc ! ProductCatalog.GetItems(brand, productKeyWords, sender))
        Behaviors.same
      case ListingResponse(ProductCatalogServiceKey.Listing(listing)) =>
        println("*************************")
        println(listing.size)
        waitingForRequest(listing)
    }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.result(system.whenTerminated, Duration.Inf)
  }

}
