package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                              extends Command
  case class RemoveItem(item: Any)                           extends Command
  case object ExpireCart                                     extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                       extends Command
  case object ConfirmCheckoutClosed                          extends Command
  case class GetItems(sender: ActorRef[Cart])                extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds
  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[Command] = empty

  def empty: Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case AddItem(item) =>
            nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
          case GetItems(sender) =>
            sender ! Cart.empty
            Behaviors.same
          case any =>
            context.log.info("Unknown Message", any)
            Behaviors.same
      }
    )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case RemoveItem(item) =>
            if (cart.contains(item)) {
              timer.cancel()
              val newCart = cart.removeItem(item)
              if (newCart.size == 0)
                empty
              else
                nonEmpty(newCart, scheduleTimer(context))
            } else
              Behaviors.same
          case AddItem(item) =>
            timer.cancel()
            nonEmpty(cart.addItem(item), scheduleTimer(context))
          case StartCheckout(orderManagerRef) =>
            timer.cancel()
            val checkoutEventMapper: ActorRef[TypedCheckout.Event] = context.messageAdapter {
              case TypedCheckout.CheckOutClosed =>
                ConfirmCheckoutClosed
            }
            val checkoutActor = context.spawn(new TypedCheckout(checkoutEventMapper).start, "checkoutActor")
            checkoutActor ! TypedCheckout.StartCheckout
            orderManagerRef ! CheckoutStarted(checkoutActor)
            inCheckout(cart)
          case ExpireCart =>
            timer.cancel()
            empty
          case GetItems(sender) =>
            sender ! cart
            Behaviors.same
      }
    )

  def inCheckout(cart: Cart): Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case ConfirmCheckoutCancelled =>
            nonEmpty(cart, scheduleTimer(context))
          case ConfirmCheckoutClosed =>
            empty
          case GetItems(sender) =>
            sender ! cart
            Behaviors.same
          case any =>
            context.log.info("Unknown Message", any)
            Behaviors.same
      }
    )

}
