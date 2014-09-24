package org.opencommercesearch.search.suggester

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

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

import org.apache.solr.client.solrj.AsyncSolrServer
import org.opencommercesearch.search.Element
import org.opencommercesearch.search.collector.Collector
import org.opencommercesearch.common.Context

/**
 * A trait for a search suggester
 *
 * @author rmerizalde
 */
trait Suggester[E <: Element] {

  /**
   * Optionally, suggesters can customize the response name for a given collector source. For example, mapping a source called
   * 'userQuery' to 'queries'
   * @param source
   * @return the response name for the given collector source
   */
  def responseName(source: String) : String = source

  /**
   * @return the sources for this suggester
   */
  def sources() : Set[String]

  /**
   * Searches and collects suggestions
   *
   * @param q is the suggestion query
   * @param site is the site to search in
   * @param collector is the suggestion collector
   * @param server is the server used to execute the search
   * @param context is the search context
   * @return a future with the given collector
   */
  def search(q: String, site: String, collector: Collector[E], server: AsyncSolrServer)(implicit context : Context) : Future[Collector[E]] = {
    searchInternal(q, site, server).map(elements => {
      for (element <- elements) {
        if (element != null) {
          collector.add(element, element.source)
        }
      }
      collector
    })
  }

  /**
   * Internal search to return the suggestion elements
   *
   * @param q is the suggestion query
   * @param site is the site to search in
   * @param server is the server used to execute the search
   * @param context is the search context
   * @return all suggestion elements
   */
  protected def searchInternal(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[E]]
}
