package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

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
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef[Any]) extends Event
}

class TypedCheckout {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds
  private def scheduleCheckoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def schedulePaymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[Command] = Behaviors.receive[Command](
    (context, command) =>
      command match {
        case StartCheckout =>
          selectingDelivery(scheduleCheckoutTimer(context))
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[Command] = Behaviors.receive[Command](
    (_, command) =>
      command match {
        case SelectDeliveryMethod(_) =>
          selectingPaymentMethod(timer)
        case ExpireCheckout =>
          cancelled
        case CancelCheckout =>
          cancelled
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[Command] = Behaviors.receive[Command](
    (context, command) =>
      command match {
        case SelectPayment(_) =>
          timer.cancel()
          processingPayment(schedulePaymentTimer(context))
        case ExpireCheckout =>
          cancelled
        case CancelCheckout =>
          cancelled
    }
  )

  def processingPayment(timer: Cancellable): Behavior[Command] = Behaviors.receive[Command](
    (_, command) =>
      command match {
        case ConfirmPaymentReceived =>
          closed
        case ExpirePayment =>
          cancelled
        case CancelCheckout =>
          cancelled
    }
  )

  def cancelled: Behavior[Command] = Behaviors.receiveMessage(any => cancelled)

  def closed: Behavior[Command] = Behaviors.stopped

}
