package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] =
    Behaviors.setup { context =>
      val http = Http(context.system)
      val result = http
        .singleRequest(HttpRequest(uri = getURI(method)))

      context.pipeToSelf(result) {
        case Success(value) => value
        case Failure(e)     => throw e
      }

      Behaviors.receiveMessage {
        case HttpResponse(StatusCodes.OK, headers, entity, _) =>
          context.log.info("Got response, ContentType: " + entity.contentType)
          payment ! PaymentSucceeded
          Behaviors.same
        case HttpResponse(error: StatusCodes.ServerError, _, _, _) =>
          throw PaymentServerError()
          Behaviors.same
        case HttpResponse(error: StatusCodes.ClientError, _, _, _) =>
          throw PaymentClientError()
          Behaviors.same
      }
    }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) =
    method match {
      case "payu"   => "http://127.0.0.1:8080"
      case "paypal" => s"http://httpbin.org/status/500"
      case "visa"   => s"http://httpbin.org/status/200"
      case _        => s"http://httpbin.org/status/404"
    }
}
