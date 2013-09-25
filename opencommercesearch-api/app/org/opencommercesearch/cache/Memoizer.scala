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

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{future, promise}
import scala.concurrent.Future

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the result value of a function
 *
 * @tparam A is the argument type of a computation
 * @tparam V is the result value's type of a computation
 */
trait Memoizer[A, V] {
  def compute(arg: A) : Future[V]
}

class DefaultMemoizer[A, V] (val computable: Computable[A, V]) extends Memoizer[A, V] {

  private val cache = new ConcurrentHashMap[A, Future[V]]

  def compute(arg: A) : Future[V] = {
    var f: Future[V] = cache.get(arg)

    if (f == null) {
      var p = promise[V]
      f = cache.putIfAbsent(arg, p.future)
      if (f == null) {
        future {
          p.success(computable.compute(arg))
        }.recover {
          case ex: Throwable => {
            cache.remove(arg, p.future)
            p.failure(ex)
          }
        }
        f = p.future
      }
    }
    f
  }

}
