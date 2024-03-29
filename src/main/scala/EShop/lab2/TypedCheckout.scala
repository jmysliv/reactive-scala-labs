package EShop.lab2

import EShop.lab3.{Payment}
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
  case object StartCheckout                                                   extends Command
  case class SelectDeliveryMethod(method: String)                             extends Command
  case object CancelCheckout                                                  extends Command
  case object ExpireCheckout                                                  extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[Event]) extends Command
  case object ExpirePayment                                                   extends Command
  case object ConfirmPaymentReceived                                          extends Command
  case object PaymentRejected                                                 extends Command
  case object PaymentRestarted                                                extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(cartActor: ActorRef[TypedCheckout.Event]) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds
  private def scheduleCheckoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def schedulePaymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case StartCheckout =>
            selectingDelivery(scheduleCheckoutTimer(context))
      }
    )

  def selectingDelivery(timer: Cancellable): Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case SelectDeliveryMethod(_) =>
            timer.cancel()
            selectingPaymentMethod(scheduleCheckoutTimer(context))
          case ExpireCheckout =>
            timer.cancel()
            cancelled
          case CancelCheckout =>
            timer.cancel()
            cancelled
      }
    )

  def selectingPaymentMethod(timer: Cancellable): Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case SelectPayment(payment, orderManagerRef) =>
            timer.cancel()
            val paymentEventMapper: ActorRef[Payment.Event] = context.messageAdapter {
              case Payment.PaymentReceived =>
                ConfirmPaymentReceived
            }
            val paymentActor = context.spawn(new Payment(payment, paymentEventMapper).start, "paymentActor")
            orderManagerRef ! PaymentStarted(paymentActor)
            processingPayment(schedulePaymentTimer(context))
          case ExpireCheckout =>
            timer.cancel()
            cancelled
          case CancelCheckout =>
            timer.cancel()
            cancelled
      }
    )

  def processingPayment(timer: Cancellable): Behavior[Command] =
    Behaviors.receive[Command](
      (_, command) =>
        command match {
          case ConfirmPaymentReceived =>
            timer.cancel()
            cartActor ! CheckOutClosed
            closed
          case ExpirePayment =>
            timer.cancel()
            cancelled
          case CancelCheckout =>
            timer.cancel()
            cancelled
      }
    )

  def cancelled: Behavior[Command] = Behaviors.receiveMessage(any => cancelled)

  def closed: Behavior[Command] = Behaviors.stopped

}
