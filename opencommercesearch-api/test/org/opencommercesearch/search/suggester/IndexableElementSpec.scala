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

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.opencommercesearch.common.Context
import play.api.i18n.Lang
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Await}
import play.api.test.FakeApplication
import play.api.test.Helpers._
import org.apache.solr.client.solrj.{SolrRequest, SolrQuery, AsyncSolrServer}
import org.opencommercesearch.api.Global._
import org.apache.solr.common.util.NamedList
import  ExecutionContext.Implicits.global
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.{SolrDocument, SolrDocumentList}

/**
 * @author jmendez
 */
class IndexableElementSpec extends Specification with Mockito {

  implicit val context = Context(preview = false, Lang("en"))

  "Suggestion" should {

    "Add to index should not return count" in {
        running(FakeApplication()) {
        setupSolrServer()

        val suggestion = mock[TestElement]

        val responseFuture = IndexableElement.addToIndex(Seq(suggestion), fetchCount = false)
        val response = Await.result(responseFuture, Duration.Inf)

        (response must not beNull)
        there was one(solrServer).request(any[SolrRequest])
        there was no(solrServer).query(any[SolrQuery])
        there was one(suggestion).toSolrDoc(anyLong, ===(0))
      }
    }

    "Add to index should return count" in {
      running(FakeApplication()) {
        setupSolrServer()

        val queryResponse2 = setupQueryResponse(2)
        val queryResponse33 = setupQueryResponse(33)


        solrServer.query(any[SolrQuery]) returns Future(queryResponse2) thenReturns Future(queryResponse33)

        val suggestion1 = mock[TestElement]
        suggestion1.id returns Some("id1")
        suggestion1.getType returns "brand"
        val suggestion2 = mock[TestElement]
        suggestion2.id  returns Some("id2")
        suggestion2.getType returns "category"

        val responseFuture = IndexableElement.addToIndex(Seq(suggestion1, suggestion2), fetchCount = true)
        val response = Await.result(responseFuture, Duration.Inf)

        (response must not beNull)
        there was one(solrServer).request(any[SolrRequest])
        there was two(solrServer).query(any[SolrQuery])

        there was one(suggestion1).toSolrDoc(anyLong, ===(2))
        there was one(suggestion2).toSolrDoc(anyLong, ===(33))

        true must beEqualTo(true)
      }
    }
  }

  def setupQueryResponse(count : Int) = {
    val doc = new SolrDocument()
    doc.addField("count", count)

    val docList = new SolrDocumentList()
    docList.setNumFound(count)
    docList.add(doc)

    val queryResponseList = new NamedList[Object]()
    queryResponseList.add("response", docList)

    val queryResponse = new QueryResponse()
    queryResponse.setResponse(queryResponseList)
    queryResponse
  }

  def setupSolrServer() = {
    solrServer = mock[AsyncSolrServer]
    val result = new NamedList[Object]()
    val responseHeader = new NamedList[Object]()
    responseHeader.add("status", "0")
    responseHeader.add("QTime", "91")

    val doc = new NamedList[Object]()
    doc.add("id", "element0")

    val response = new NamedList[Object]()
    response.add("numFound", "1")
    response.add("start", "0")
    response.add("docs", Seq(doc))

    result.add("responseHeader", responseHeader)
    result.add("response", response)
    solrServer.request(any[SolrRequest]) returns Future(response)
    solrServer
  }
}
