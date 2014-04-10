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

import org.opencommercesearch.search.Element
import org.opencommercesearch.search.collector.Collector
import org.apache.solr.client.solrj.{AsyncSolrServer}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author rmerizalde
 */
trait Suggester[E <: Element] {

  def search(q: String, site: String, collector: Collector[E], server: AsyncSolrServer) : Future[Collector[E]] = {
    searchInternal(q, site, server).map(elements => {
      for (element <- elements) {
        collector.add(element, element.source)
      }
      collector
    })
  }

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer) : Future[Seq[E]]
}
