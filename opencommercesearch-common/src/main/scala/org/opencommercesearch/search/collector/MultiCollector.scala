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
 * Trait for collector with multiple suggestion sources
 *
 * @author rmerizalde
 */
trait MultiCollector[E <: Element] extends Collector[E] {

  /**
   * @return the sources for this collector
   */
  def sources : Iterable[String]

  /**
   * @return the collectors in this multi-collector
   */
  def collectors : Iterable[Collector[E]]

  /**
   * @param source the source of the collector
   * @return the collector for the given source
   */
  def collector(source: String) : Option[Collector[E]]

  /**
   * Adds the given collector
   * @param source the source of the new collector
   * @param collector the new collector
   *
   * @return <code>true</code> if the collector changed, otherwise return <code>false</code>
   */
  def add(source: String, collector: Collector[E]) : Boolean

}
