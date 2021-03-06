package nodescala

import NodeScala._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.async.Async.async
import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite with Matchers {

  val value1 = 200
  val value2 = 300

  test("A Future should always be completed") {
    val always = Future.always(value1)
    assert(Await.result(always, 0 nanos) == value1)
  }

  test("A Future should never be completed") {
    val never = Future.never[Int]
    try {
      Await.result(never, 1 second)
      fail()
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("CancellationTokenSource should allow stopping the computation") {
    val cts = CancellationTokenSource()
    val ct = cts.cancellationToken
    val p = Promise[String]()

    async {
      while (ct.nonCancelled) {
        // do work
      }

      p.success("done")
    }

    cts.unsubscribe()
    assert(Await.result(p.future, 1 second) == "done")
  }

  test("CancellationTokenSource should allow cancel the computation with Future running") {
    val p = Promise[String]()
    val cts = Future.run() { token =>
      async {
        while (token.nonCancelled) {
          // do work
        }

        p.success("done")
      }
    }
    cts.unsubscribe()
    Await.result(p.future, 1 second) shouldBe "done"
  }

  test("combined futures") {
    val f1 = Future {
      Future.delay(100 millisecond)
      value1
    }
    val f2 = Future {
      Future.delay(100 millisecond)
      value2
    }
    val f3 = Future.never

    val succeeding = Future.all( f1::f2::Nil)
    val failing = Future.all(f1::f2::f3::Nil)

    Await.result(succeeding, 100 millisecond) shouldBe List(value1, value2)
    try {
      Await.result(failing, 100 millisecond)
      fail()
    } catch {
      case e: TimeoutException => //correct
    }
  }

  test("combined completed with any") {
    val f1 = Future {
      Future.delay(100 millisecond)
      value1
    }
    val f2 = Future {
      Future.delay(100 millisecond)
      value1
    }
    val f3 = Future {
      Future.delay(100 millisecond)
      throw new Exception
    }

    val succeeding = Future.any(f1::f2::Nil)
    val failing = Future.any(f1::f2::f3::Nil)
    Await.result(succeeding, 200 millisecond) shouldBe value1
    try {
      Await.result(failing, 500 millisecond)
      fail()
    } catch {
      case e: Exception => // correct
    }
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()
    def write(s: String) {
      response += s
    }
    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("unsubscribe after completion") {
    val cts = Future.run() { ct =>
      async {
        while (ct.nonCancelled) {
          // processing...
        }
      }
    }

    Future.delay(500 millisecond) onSuccess {
      case _ => cts.unsubscribe()
    }
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }
}
