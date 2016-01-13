package org.opencommercesearch.api.controllers


import org.junit.Assert._
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.{Before, Specification}
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class Test {
  var next = List.empty[Future[String]];

  def init(inList: List[Future[String]]): Test = {
    next = inList
    this
  }

  def testFunct() = {
    val ret = next.head
    next = next.tail
    ret
  }
}

@RunWith(classOf[JUnitRunner])
class RetrytingSpec extends Specification with Mockito {

  val retryTrait = new Retrying {}

  trait Retry extends Before {
    def before = {
    }
  }

  sequential

  "Retrying" should {
    "return ok cause no retry was needed" in new Retry {
      val test = new Test().init(List(Future.successful("success")))
      val future = retryTrait.retry(test.testFunct(), 2).recover {
        case e: Throwable => {
          fail("Unexpected exception thrown.")
        }
      }
      Await.result(future, Duration.Inf)
    }

    "return ok after failing first intent" in new Retry {

      val test = new Test().init(List(Future.failed(new Exception("error")), Future.successful("success")))
      val future = retryTrait.retry(test.testFunct(), 2)
        .recover {
        case e: Throwable => {
          fail("Unexpected exception thrown.")
        }
      }
      Await.result(future, Duration.Inf)
    }

    "fail after trying two times" in new Retry {

      val test = new Test().init(List(Future.failed(new Exception("error")), Future.failed(new Exception("error2")),
        Future.successful("success")))

      val future = retryTrait.retry(test.testFunct(), 2).map(resp => {
        fail("Exception expected.")
      }).recover {
        case e: Throwable => // nothing to do here. failure expected
      }
      Await.result(future, Duration.Inf)
    }
  }
}