package org.opencommercesearch.cache

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import scala.concurrent.ExecutionContext.Implicits.global

import org.specs2.mutable._
import org.specs2.mock.Mockito
import java.util.concurrent.CountDownLatch

import scala.concurrent.Future

class DefaultMemoizerSpec extends Specification with Mockito {

  class DummyResult {
    def foo : String = {
      "any"
    }
  }

  "Memoizer" should {
    /**
     * This test case will potentially test the case where two threads enter the non-atomic
     * if statement. There no clean way to guarantee execution order though
     */
    "should compute once even when two threads enter the non-atomic if statement" in {
      val result = mock[DummyResult]
      val endGate = new CountDownLatch(2)

      result.foo returns "one"
      val computable = new Computable[String, DummyResult] {
        def compute(arg: String) : DummyResult = {
          result.foo
          result
        }
      }
      val memoizer = new DefaultMemoizer[String, DummyResult](computable)
      var f1: Future[DummyResult] = null
      var f2: Future[DummyResult] = null

      val t1 = new Thread(new Runnable() {
        @Override
        def run() : Unit = {
          f1 = memoizer.compute("1")
          f1.map(s => {
            endGate.countDown()
          })
        }
      }, "test1-thread-1")

      val t2 = new Thread(new Runnable() {
        @Override
        def run() : Unit = {
          f2 = memoizer.compute("1")
          f2.map(s => {
            endGate.countDown()
          })
        }
      }, "test1-thread-2")

      t1.start()
      t2.start()
      endGate.await()
      f1.map({s =>
        s must be(result)
      })
      f2.map({s =>
        s must be(result)
      })
      f1 must be(f2)
      there was one(result).foo
    }
  }

  "should compute once when value is already cached" in {
    val result = mock[DummyResult]
    val startGate = new CountDownLatch(1)
    val endGate = new CountDownLatch(2)

    result.foo returns "one"
    val computable = new Computable[String, DummyResult] {
      def compute(arg: String) : DummyResult = {
        result.foo
        result
      }
    }
    val memoizer = new DefaultMemoizer[String, DummyResult](computable)
    var f1: Future[DummyResult] = null
    var f2: Future[DummyResult] = null

    val t1 = new Thread(new Runnable() {
      @Override
      def run() : Unit = {
        f1 = memoizer.compute("1")
        f1.map(s => {
          endGate.countDown()
          startGate.countDown()
        })
      }
    }, "test2-thread-1")

    val t2 = new Thread(new Runnable() {
      @Override
      def run() : Unit = {
        startGate.await()
        f2 = memoizer.compute("1")
        f2.map(s => {
          endGate.countDown()
        })
      }
    }, "test2-thread-2")

    t1.start()
    t2.start()
    endGate.await()
    f1.map({s =>
      s must be(result)
    })
    f2.map({s =>
      s must be(result)
    })
    f1 must be(f2)
    there was one(result).foo
  }

  "should not cache when exception occurs" in {
    val result = mock[DummyResult]
    val startGate = new CountDownLatch(1)
    val endGate = new CountDownLatch(2)

    result.foo returns "one"
    val computable = new Computable[String, DummyResult] {
      def compute(arg: String) : DummyResult = {
        if (result.foo.equals("one")) {
          throw new Exception("just fail")
        }
        result
      }
    }
    val memoizer = new DefaultMemoizer[String, DummyResult](computable)
    var f1: Future[DummyResult] = null
    var f2: Future[DummyResult] = null

    val t1 = new Thread(new Runnable() {
      @Override
      def run() : Unit = {
        f1 = memoizer.compute("1")
        f1.recover {
          case _ => {
            endGate.countDown()
            startGate.countDown()
          }
        }
      }
    }, "test3-thread-1")

    val t2 = new Thread(new Runnable() {
      @Override
      def run() : Unit = {
        startGate.await()
        result.foo returns "two"
        f2 = memoizer.compute("1")
        f2.map(s => {
          endGate.countDown()
        })
      }
    }, "test3-thread-2")

    t1.start()
    t2.start()
    endGate.await()
    f1.recover {
      case ex: IllegalArgumentException => {
        ex.getMessage must be("just fail")
      }
    }
    f2.map({s =>
      s must be(result)
    })
    f1 must not be(f2)
    there was two(result).foo
  }
}
