package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case AddItem(item, sender) =>
            val cartActor = context.spawn(new TypedCartActor().start, "cartActor")
            cartActor ! TypedCartActor.AddItem(item)
            sender ! Done
            open(cartActor)
      }
    )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case AddItem(item, sender) =>
            cartActor ! TypedCartActor.AddItem(item)
            sender ! Done
            Behaviors.same
          case RemoveItem(item, sender) =>
            cartActor ! TypedCartActor.RemoveItem(item)
            sender ! Done
            Behaviors.same
          case Buy(sender) =>
            val cartEventMapper: ActorRef[TypedCartActor.Event] = context.messageAdapter {
              case TypedCartActor.CheckoutStarted(checkoutRef) =>
                ConfirmCheckoutStarted(checkoutRef)
            }
            cartActor ! TypedCartActor.StartCheckout(cartEventMapper)
            inCheckout(cartActor, sender)
      }
    )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive[Command](
      (_, command) =>
        command match {
          case ConfirmCheckoutStarted(checkoutRef) =>
            senderRef ! Done
            inCheckout(checkoutRef)
      }
    )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
            checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
            checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
            inPayment(sender)
      }
    )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case ConfirmPaymentStarted(paymentRef) =>
            senderRef ! Done
            inPayment(paymentRef, senderRef)
          case ConfirmPaymentReceived =>
            senderRef ! Done
            finished
      }
    )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive[Command](
      (context, command) =>
        command match {
          case Pay(sender) =>
            paymentActorRef ! Payment.DoPayment
            inPayment(sender)
      }
    )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
