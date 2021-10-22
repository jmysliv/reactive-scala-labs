package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds
  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[Command] = empty

  def empty: Behavior[Command] = Behaviors.receive[Command](
    (context, command) =>
      command match {
        case AddItem(item) =>
          nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[Command] = Behaviors.receive[Command](
    (_, command) =>
      command match {
        case RemoveItem(item) =>
          if (cart.contains(item)) {
            val newCart = cart.removeItem(item)
            if (newCart.size == 0) {
              empty
            } else {
              nonEmpty(newCart, timer)
            }
          } else {
            Behaviors.same
          }
        case AddItem(item) =>
          nonEmpty(cart.addItem(item), timer)
        case StartCheckout =>
          inCheckout(cart)
        case ExpireCart =>
          empty
    }
  )

  def inCheckout(cart: Cart): Behavior[Command] = Behaviors.receive[Command](
    (context, command) =>
      command match {
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          empty
    }
  )

}
