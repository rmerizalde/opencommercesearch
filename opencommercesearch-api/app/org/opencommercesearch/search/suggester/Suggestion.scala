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

import org.apache.solr.common.SolrInputDocument
import org.apache.commons.lang.StringUtils
import java.util.Date
import java.text.SimpleDateFormat
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.opencommercesearch.api.Global._
import org.opencommercesearch.common.Context
import scala.concurrent.{ExecutionContext, Future}
import org.apache.solr.client.solrj.response.UpdateResponse
import scala.collection.JavaConversions._
import ExecutionContext.Implicits.global

/**
 * This trait provides functionality to convert a model object
 * into a solr autocomplete collection document
 *
 * @author jmendez
 * @author nkumar
 */
trait Suggestion {
  val IsoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  /**
   * Converts a given instance into a Solr input document compatible with the suggestion collection.
   * @param feedTimestamp a feed timestamp
   * @return Solr input document for suggestion collection.
   */
  def toSuggestionDoc(feedTimestamp: Long) : SolrInputDocument = {
    val doc = new SolrInputDocument()
    val `type` = getType
    doc.addField("id", `type` + "-" + getId)
    doc.addField("userQuery", StringUtils.EMPTY)
    doc.addField("ngrams", getNgramText)
    doc.addField("type", `type`)
    doc.addField("feedTimestamp", feedTimestamp)
    doc.addField("count", 1)
    doc.addField("lastUpdated", IsoDateFormat.format(new Date()))

    val sites = getSites

    if(sites != null) {
      sites.foreach { site =>
        doc.addField("siteId", site)
      }
    }

    doc
  }

  def getId : String
  def getNgramText : String
  def getType : String
  def getSites : Seq[String]
}

object Suggestion {
  def addToIndex(suggestions : Seq[Suggestion])(implicit context: Context) : Future[UpdateResponse] = {
    //Add category info to the suggestion collection
    if(context.isPublic) {
      val feedTimeStamp = System.currentTimeMillis()
      val updateQuery = new AsyncUpdateRequest()
      updateQuery.setParam("collection", SuggestCollection)
      updateQuery.add(suggestions map { s =>
        s.toSuggestionDoc(feedTimeStamp)
      })

      updateQuery.process(solrServer)
    }
    else {
      Future(null)
    }
  }
}
