package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case class DoPayment(orderManagerRef: ActorRef[Event]) extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment(
  method: String,
  checkout: ActorRef[Payment.Event]
) {

  import Payment._

  def start: Behavior[Payment.Command] =
    Behaviors.receive(
      (context, command) =>
        command match {
          case DoPayment(orderManagerRef) =>
            orderManagerRef ! PaymentReceived
            checkout ! PaymentReceived
            Behaviors.stopped
      }
    )

}
