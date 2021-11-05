package EShop.lab4

import EShop.lab2.TypedCheckout
import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command], command: Command): Cancellable =
    context.scheduleOnce(timerDuration, context.self, command)

  def apply(cartActor: ActorRef[TypedCheckout.Event], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCheckout.Event]
  ): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case WaitingForStart =>
          command match {
            case StartCheckout => Effect.persist(CheckoutStarted)
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case SelectingDelivery(timer) =>
          command match {
            case SelectDeliveryMethod(method) =>
              timer.cancel()
              Effect.persist(DeliveryMethodSelected(method))
            case ExpireCheckout =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case CancelCheckout =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case SelectingPaymentMethod(timer) =>
          command match {
            case SelectPayment(payment, orderManagerRef) =>
              timer.cancel()
              val paymentEventMapper: ActorRef[Payment.Event] = context.messageAdapter {
                case Payment.PaymentReceived =>
                  ConfirmPaymentReceived
              }
              val paymentActor = context.spawn(new Payment(payment, paymentEventMapper).start, "paymentActor")
              Effect.persist(PaymentStarted(paymentActor)).thenRun { _ =>
                orderManagerRef ! PaymentStarted(paymentActor)
              }
            case ExpireCheckout =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case CancelCheckout =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case ProcessingPayment(timer) =>
          command match {
            case ConfirmPaymentReceived =>
              timer.cancel()
              Effect.persist(CheckOutClosed).thenRun { _ =>
                cartActor ! CheckOutClosed
              }
            case ExpirePayment =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case CancelCheckout =>
              timer.cancel()
              Effect.persist(CheckoutCancelled)
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case Cancelled =>
          command match {
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case Closed =>
          command match {
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }
      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      event match {
        case CheckoutStarted           => SelectingDelivery(schedule(context, ExpireCheckout))
        case DeliveryMethodSelected(_) => SelectingPaymentMethod(schedule(context, ExpireCheckout))
        case PaymentStarted(_)         => ProcessingPayment(schedule(context, ExpirePayment))
        case CheckOutClosed            => Closed
        case CheckoutCancelled         => Cancelled
      }
    }
}
