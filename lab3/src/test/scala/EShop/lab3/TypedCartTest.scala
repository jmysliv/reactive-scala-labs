package EShop.lab3

import EShop.lab2.TypedCartActorTest.{cartActorWithCartSizeResponseOnStateChange, emptyMsg, inCheckoutMsg, nonEmptyMsg}
import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.testkit.TestActorRef
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("AGH")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)
  }

  it should "be empty after adding and removing the same item" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("AGH")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! RemoveItem("AGH")

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)
  }

  it should "start checkout" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("AGH")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! StartCheckout(testKit.createTestProbe[TypedCartActor.Event]().ref)

    probe.expectMessage(inCheckoutMsg)
    probe.expectMessage(1)
  }
}
