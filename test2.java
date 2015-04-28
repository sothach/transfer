package nodescala

import java.util.NoSuchElementException

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.Timeouts
import org.scalatest.junit.JUnitRunner

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class MoreTests extends FunSuite with Matchers with Timeouts {

  val value1 = 200
  val value2 = 300

  test("never timesout") {
    failAfter(1000 millis) {
      try {
        Await.result(Future.never, 500 nano)
      } catch {
        case e: TimeoutException => // success
      }
    }
  }

  test("all combinator: all returned") {
    val result = Await.result(Future.all(List(Future.always(value1), Future.always(value2))), 1 second)
    result shouldBe List(value1,value2)
  }

  test("all combinator: failure") {
    val f = Future.all(
      List(Future.always(value1), Future { new RuntimeException }, Future.always(value2)))
    try {
      Await.result(f, 1 second)
      fail()
    } catch {
      case e: RuntimeException =>
    }
  }

  test("any combinator: first returned") {
    val result = Await.result(
      Future.any(List(Future.always(value1), Future.never)), 1 second)
    result shouldBe value1
  }

  test("any combinator: never-never timesout") {
    try {
      Await.result(
        Future.any(List(Future.never, Future.never)), 1 second)
      fail()
    } catch {
      case e: TimeoutException =>
    }
  }

  test("delay: elapsed correct") {
    val start = System.currentTimeMillis()
    Await.ready(Future.delay(500 millisecond), 1 second)
    val elapsed = System.currentTimeMillis() - start
    (500 to 1000) should contain (elapsed)
  }

  test("delay: times-out") {
    val f1 = Future.delay(100 millisecond)
    Await.result(f1, 100 millisecond)

    val f2 = Future.delay(500 millisecond)
    try {
      Await.result(f2, 100 millisecond)
      fail()
    } catch {
      case e: TimeoutException => // correct
    }
  }

  test("now combinator") {
    Future.always(value1).now shouldBe value1
  }

  test("now combinator: fails") {
    val f = Future {
      throw new RuntimeException
    }
    try {
      f.now
      fail()
    } catch {
      case e: NoSuchElementException =>
    }
  }

  test("now combinator: not completed") {
    val f = Future {
      Future.delay(500 millisecond)
      value1
    }
    try {
      f.now
      fail()
    } catch {
      case e: NoSuchElementException => // correct
    }
  }

  test("now combinator: throws exception") {
    val thrown = Future {
      throw new RuntimeException
    }
    try {
      thrown.now
      fail()
    } catch {
      case e: RuntimeException => // correct
    }
  }

  test("continueWith combinator") {
    val f = Future[Int] {
      Future.delay(500 millisecond)
      value1
    }
    val continue = f.continueWith(_.now + 100)
    val value = Await.result(continue, 1 second)
    value shouldBe value2
  }

  test("continueWith combinator: fail") {
    val f = Future[Int] {
      Future.delay(500 millisecond)
      throw new RuntimeException
    }
    try {
      f.continueWith(_.now + 100)
      fail()
    } catch {
      case e: RuntimeException => // correct
    }
  }

  test("continueWith: second fails") {
    val first = Future { value1 }
    val second = Future[Int] { throw new RuntimeException }
    Await.result(first.continueWith(_.value.get.get + value2), 100 millis) shouldBe (value1+value2)

    try {
      Await.result(second.continueWith(_.value.get.get), 100 millis)
      fail()
    } catch {
      case e: RuntimeException => // correct
    }
  }

  test("continue combinator") {
    val f = Future[Int] {
      Future.delay(500 millisecond)
      value1
    }
    val continue = f.continue(_.get + 100)
    val result = Await.result(continue, 1 second)
    result shouldBe value2
  }

  test("continue combinator: fail") {
    val f = Future[Int] {
      Future.delay(500 millisecond)
      throw new RuntimeException
    }
    try {
      f.continue(_.get + 100)
      fail()
    } catch {
      case e: RuntimeException => // correct
    }
  }

}

