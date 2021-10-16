package EShop.lab2

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher
  private def scheduleTimer: Cancellable                  = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = LoggingReceive {
    case AddItem(item) =>
      context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case RemoveItem(item) =>
      if( cart.contains(item)){
        val newCart = cart.removeItem(item)
        if (newCart.size == 0) {
          context.become(empty)
        } else {
          context.become(nonEmpty(newCart, timer))
        }
      }
    case AddItem(item) =>
      context.become(nonEmpty(cart.addItem(item), timer))
    case StartCheckout =>
      context.become(inCheckout(cart))
    case ExpireCart =>
      context.become(empty)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context.become(nonEmpty(cart, scheduleTimer))
    case ConfirmCheckoutClosed =>
      context.become(empty)
  }
}

object CartApp extends App {
  import CartActor._

  val system    = ActorSystem("CartActor")
  val cartActor = system.actorOf(Props[CartActor])

  cartActor ! AddItem(5)
  cartActor ! RemoveItem(5)
  cartActor ! AddItem(2)
  cartActor ! AddItem(5)
  cartActor ! RemoveItem(5)
  cartActor ! StartCheckout
  cartActor ! ConfirmCheckoutClosed

  import scala.concurrent.Await

  Await.result(system.whenTerminated, Duration.Inf)
}
