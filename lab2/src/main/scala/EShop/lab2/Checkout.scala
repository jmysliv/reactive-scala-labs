package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration                               = 1 seconds
  val paymentTimerDuration                                = 1 seconds
  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  private def scheduleCheckoutTimer: Cancellable          = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer: Cancellable           = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context.become(selectingDelivery(scheduleCheckoutTimer))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(_) =>
      context.become(selectingPaymentMethod(timer))
    case ExpireCheckout =>
      context.become(cancelled)
    case CancelCheckout =>
      context.become(cancelled)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(_) =>
      context.become(processingPayment(schedulePaymentTimer))
    case ExpireCheckout =>
      context.become(cancelled)
    case CancelCheckout =>
      context.become(cancelled)
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      context.become(closed)
    case ExpirePayment =>
      context.become(cancelled)
    case CancelCheckout =>
      context.become(cancelled)
  }

  def cancelled: Receive = LoggingReceive {
    case any =>
  }

  def closed: Receive = LoggingReceive {
    case any =>
  }
}

object CheckoutApp extends App {
  import Checkout._

  val system        = ActorSystem("CheckoutActor")
  val checkoutActor = system.actorOf(Props[Checkout])

  checkoutActor ! StartCheckout
  checkoutActor ! SelectDeliveryMethod("inpost")
  checkoutActor ! SelectPayment("credit card")
  checkoutActor ! ConfirmPaymentReceived

  import scala.concurrent.Await

  Await.result(system.whenTerminated, Duration.Inf)
}
