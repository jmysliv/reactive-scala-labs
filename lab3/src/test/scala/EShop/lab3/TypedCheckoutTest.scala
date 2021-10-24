package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartActor         = testKit.createTestProbe[TypedCheckout.Event]
    val orderManagerActor = testKit.createTestProbe[TypedCheckout.Event]
    val checkoutActor     = testKit.spawn(new TypedCheckout(cartActor.ref).start)

    checkoutActor ! StartCheckout
    checkoutActor ! SelectDeliveryMethod("order")
    checkoutActor ! SelectPayment("paypal", orderManagerActor.ref)
    checkoutActor ! ConfirmPaymentReceived
    cartActor.expectMessage(TypedCheckout.CheckOutClosed)
  }

}
