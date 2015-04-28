package nodescala

import java.text.SimpleDateFormat
import java.util.Calendar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

object Main {

  def main(args: Array[String]) {
    // 1. instantiate the server at 8191, relative path "/test",
    //    and have the response return headers of the request
    val myServer = new NodeScala.Default(8191)
    val myServerSubscription = myServer.start("/test") { request =>
      for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // 2. create a future that expects some user input `x`
    //    and continues with a `"You entered... " + x` message
    val start = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime)
    val userInterrupted = Future.userInput(f"Hit ENTER to cancel... $start") continueWith {
      f => f"You entered... ${f.now}"
    }

    // TO IMPLEMENT
    // 3. create a future that completes after 20 seconds
    //    and continues with a `"Server timeout!"` message
    val timeOut: Future[String] = Future.delay(20 seconds).continueWith(_ => "Server timeout!")

    // TO IMPLEMENT
    // 4. create a future that completes when either 20 seconds elapse
    //    or the user enters some text and presses ENTER
    val terminationRequested: Future[String] = Future.any(List(userInterrupted, timeOut))

    // TO IMPLEMENT
    // 5. unsubscribe from the server
    terminationRequested onSuccess {
      case msg =>
        println(msg)
        val end = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime)
        myServerSubscription.unsubscribe()
        println(f"Bye! ($end)")
    }
  }

}
