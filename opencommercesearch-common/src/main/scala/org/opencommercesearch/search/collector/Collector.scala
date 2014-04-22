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

/**
 * Trait for suggestion collectors
 *
 * @author rmerizalde
 */
trait Collector[E <: Element] {
  /**
   * @return <code>true</code> if the collector can stop collecting elements. Otherwise return <code>false</code>
   */
  def canStop : Boolean

  /**
   * @return <code>true</code> if the collector has no elements. Otherwise returns <code>false</code>
   */
  def isEmpty : Boolean

  /**
   * Adds an element to this collector
   * @param element the element to be added
   * @param source the element source
   *
   * @return <code>true</code> if the collector changed, otherwise return <code>false</code>
   */
  def add(element: E, source: String) : Boolean

  /**
   * @return the list of elements in the collector
   */
  def elements() : Seq[E]

  /**
   * @return the number of elements in the collector
   */
  def size() : Int

  /**
   * @return the capacity of this collector
   */
  def capacity() : Int

}
