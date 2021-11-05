package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        Empty,
        commandHandler(context),
        eventHandler(context)
      )
    }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case Empty =>
          command match {
            case AddItem(item) => Effect.persist(ItemAdded(item))
            case GetItems(sender) =>
              sender ! Cart.empty
              Effect.none
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case NonEmpty(cart, timer) =>
          command match {
            case RemoveItem(item) if !cart.contains(item) => Effect.none
            case RemoveItem(_) if cart.size == 1 =>
              timer.cancel()
              Effect.persist(CartEmptied)
            case RemoveItem(item) =>
              timer.cancel()
              Effect.persist(ItemRemoved(item))
            case AddItem(item) =>
              timer.cancel()
              Effect.persist(ItemAdded(item))
            case StartCheckout(orderManagerRef) =>
              timer.cancel()
              val checkoutEventMapper: ActorRef[TypedCheckout.Event] = context.messageAdapter {
                case TypedCheckout.CheckOutClosed =>
                  ConfirmCheckoutClosed
              }
              val checkoutActor = context.spawn(new TypedCheckout(checkoutEventMapper).start, "checkoutActor")
              Effect.persist(CheckoutStarted(checkoutActor)).thenRun { _ =>
                checkoutActor ! TypedCheckout.StartCheckout
                orderManagerRef ! CheckoutStarted(checkoutActor)
              }
            case ExpireCart =>
              Effect.persist(CartExpired)
            case GetItems(sender) =>
              sender ! cart
              Effect.none
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }

        case InCheckout(cart) =>
          command match {
            case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
            case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
            case GetItems(sender) =>
              sender ! cart
              Effect.none
            case any =>
              context.log.info("Unknown Message", any)
              Effect.none
          }
      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      event match {
        case CheckoutStarted(_)        => InCheckout(state.cart)
        case ItemAdded(item)           => NonEmpty(state.cart.addItem(item), scheduleTimer(context))
        case ItemRemoved(item)         => NonEmpty(state.cart.removeItem(item), scheduleTimer(context))
        case CartEmptied | CartExpired => Empty
        case CheckoutClosed            => Empty
        case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
      }
    }

}
