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

import org.opencommercesearch.api.i18n.Lang
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.JavaConversions._
import scala.concurrent.Future

import java.text.SimpleDateFormat
import java.util.Date

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.ProductQuery
import org.opencommercesearch.api.controllers.BrandController._
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.Element

import org.apache.commons.lang.StringUtils
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.common.SolrInputDocument

/**
 * This trait provides functionality to convert a model object
 * into a Solr suggestion collection document
 *
 * @author jmendez
 * @author nkumar
 */
trait IndexableElement extends Element {
  import org.opencommercesearch.search.suggester.IndexableElement._

  /**
   * Converts this instance into a Solr input document compatible with the suggestion collection.
   * @param feedTimestamp a feed timestamp
   * @return Solr input document for suggestion collection.
   */
  def toSolrDoc(feedTimestamp: Long, count: Int = 1) : SolrInputDocument = {
    val doc = new SolrInputDocument()
    val `type` = getType
    doc.addField("id", getSuggestionId(`type`,  id.get))
    doc.addField("userQuery", StringUtils.EMPTY)
    doc.addField("ngrams", getNgramText)
    doc.addField("type", `type`)
    doc.addField("feedTimestamp", feedTimestamp)
    doc.addField("count", count)
    doc.addField("lastUpdated", IsoDateFormat.get().format(new Date()))

    val sites = getSites

    if(sites != null) {
      sites.foreach { site =>
        doc.addField("siteId", site)
      }
    }

    doc
  }

  def getNgramText : String
  def getType : String
  def getSites : Seq[String]
}

object IndexableElement {
  val IsoDateFormat = new ThreadLocal[SimpleDateFormat]() {
    protected override def initialValue() : SimpleDateFormat = {
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    }
  }

  /**
   * Forms a valid suggestion ID based on the given element type.
   * @param type An element type
   * @param id The element id
   */
  private def getSuggestionId(`type`: String, id: String) : String = {
    `type` + "-" + id
  }

  /**
   * Adds an indexable element to the suggest collection
   * @param elements List of elements to index
   * @return Future with the index result
   */
  def addToIndex(elements : Seq[IndexableElement], fetchCount: Boolean = false,feedTimeStamp:Long = System.currentTimeMillis())(implicit context: Context) : Future[UpdateResponse] = {
    if (context.isPublic && context.lang == Lang.English) {
      val solrDocs = getDocsToIndex(elements, fetchCount, feedTimeStamp)

      val updateQuery = new AsyncUpdateRequest()
      updateQuery.setParam("collection", SuggestCollection)

      solrDocs flatMap { docs =>
        Logger.debug(s"Adding ${docs.size} elements out of ${elements.size} to index.")
        updateQuery.add(docs)

        if (updateQuery.getDocuments.size() > 0) {
          updateQuery.process(solrServer)
        }
        else {
          Future(null)
        }
      }
    }
    else {
      Future(null)
    }
  }

  private def getDocsToIndex(elements: Seq[IndexableElement], fetchCount: Boolean, feedTimeStamp: Long)(implicit context: Context): Future[Seq[SolrInputDocument]] = {
    if(elements.size == 0) {
      Future.successful(Seq.empty)
    }
    else {
      def toSolrInputDocuments = {
        elements map { e =>
          Future(Some(e.toSolrDoc(feedTimeStamp)))
        }
      }

      val docsToIndex =
        if (fetchCount) {
          getElementsInStock(elements) map { elementsInStock =>
            val update = withSuggestCollection(new AsyncUpdateRequest())

            elements map { e =>
              try {
                elementsInStock.getOrElse(e.id.get, 0L) match {
                  case count: Long if count > 0 =>
                    //Add the count to the element
                    Logger.debug(s"Element $e has products on stock")
                    Future(Some(e.toSolrDoc(feedTimeStamp, count.toInt)))
                  case _ =>
                    //Actually, delete this one from suggestions collection
                    val id = getSuggestionId(e.getType, e.id.get)
                    update.deleteById(id)
                    update.process(solrServer) map { delete =>
                      Logger.debug(s"Suggestion element $id was deleted because it no longer has products on stock.")
                      None
                    }
                }
              }
              catch {
                case exception: Exception =>
                  Logger.error(s"Failed to check stock for elements $e", exception)
                  Future(Option(e.toSolrDoc(feedTimeStamp)))
              }
            }
          }
        }
        else {
          Future.successful(toSolrInputDocuments)
        }

      docsToIndex flatMap { docs =>
        Future.sequence(docs) map (docs => {
          docs.collect {
            case doc: Some[SolrInputDocument] => doc.get
          }
        })
      }
    }
  }

  private def getElementsInStock(elements: Seq[IndexableElement])(implicit context: Context) : Future[Map[String, Long]] = {
    def getField(elements : Seq[IndexableElement]) : String = {
      elements(0).getType match {
        case "brand" => "brandId"
        case "category" => "ancestorCategoryId"
      }
    }

    val productQuery = new ProductQuery("*:*")
    productQuery.withoutToos()
    productQuery.setRows(0)
    productQuery.withFaceting(getField(elements), Option(MaxFacetPaginationLimit))

    solrServer.query(productQuery) map { response =>
      response.getFacetFields.flatMap({ facet =>
        facet.getValues map { value =>
          value.getName -> value.getCount
        }
      }).toMap
    }
  }
}
