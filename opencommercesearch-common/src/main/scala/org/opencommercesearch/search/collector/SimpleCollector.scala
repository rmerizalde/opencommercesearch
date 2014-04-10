package org.opencommercesearch.search.collector

/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import org.opencommercesearch.search.Element
import scala.collection.mutable

/**
 * @author rmerizalde
 */
class SimpleCollector[E <: Element](val capacity: Int) extends Collector[E] {

  def this() = this(SimpleCollector.DefaultCapacity)

  val elementList = new mutable.ArrayBuffer[E](capacity)

  override def isEmpty: Boolean = elementList.isEmpty

  override def canStop: Boolean = false

  override def add(element: E, source: String): Boolean = {
    if (elementList.size < capacity) {
      elementList.append(element)
      true
    } else {
      false
    }
  }

  override def elements() : Seq[E] = elementList

  override def size() : Int = elementList.size
}

object SimpleCollector {
  val DefaultCapacity = 10
}
