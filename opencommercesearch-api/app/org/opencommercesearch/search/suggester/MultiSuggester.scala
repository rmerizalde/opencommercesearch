package org.opencommercesearch.search.suggester

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

import scala.collection.mutable
import scala.concurrent.Future

import org.apache.solr.client.solrj.AsyncSolrServer
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.Element
import org.opencommercesearch.search.collector.Collector

/**
 * Suggests any type of element. By default, it only uses the catalog suggester. Other suggesters
 * can be plugged in via configuration properties.
 *
 * @author rmerizalde
 */
class MultiSuggester extends Suggester[Element] {

  private var suggesters = Seq[Suggester[Element]]()

  private var allSources = Set.empty[String]

  private def init() : Unit = {
    val multiElementSuggester = new CatalogSuggester[Element]
    suggesters = suggesters :+ multiElementSuggester

    // plug other suggesters from the config here

    allSources = suggesters.foldLeft(allSources)(_ ++ _.sources())
  }

  init()

  override def sources() = allSources

  override def responseName(source: String): String = suggesters.collectFirst({
    case s if s.sources().contains(source) => s.responseName(source)
  }).getOrElse(source)

  override def search(q: String, site: String, collector: Collector[Element], server: AsyncSolrServer)(implicit context : Context) : Future[Collector[Element]] = {
    val futureList = new mutable.ArrayBuffer[Future[Collector[Element]]](suggesters.size)

    suggesters.foreach( suggester => {
      futureList += suggester.search(q, site, collector, server)
    })

    Future.sequence(futureList).map( _ => {
      collector
    })
  }

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[Element]] = {
    // this suggester simply leverages other suggester's
    throw new UnsupportedOperationException()
  }
}
