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
import org.specs2.mock._
import org.opencommercesearch.common.Context
import play.api.i18n.Lang
import scala.concurrent.{ExecutionContext, Future, Await}
import play.api.test.FakeApplication
import play.api.test.Helpers._
import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}
import org.opencommercesearch.api.Global._
import ExecutionContext.Implicits.global
import org.apache.solr.client.solrj.response.QueryResponse
import org.opencommercesearch.search.Element

import org.opencommercesearch.search.collector.SimpleCollector
import org.apache.solr.common.util.{SimpleOrderedMap, NamedList}
import org.apache.solr.common.{SolrDocument, SolrDocumentList}
import java.util
import org.opencommercesearch.api.service.{MongoStorageFactory, MongoStorage}
import org.opencommercesearch.api.models.{Product, Category, Brand}
import scala.concurrent.duration.Duration

/**
 * @author jmendez
 */
class CatalogSuggesterSpec extends Specification with Mockito {
  implicit val context = Context(preview = true, Lang("en"))

  "CatalogSuggester" should {

    "Return some suggestions" in {
      running(FakeApplication()) {
        val suggester = new CatalogSuggester[Element]()
        val solrServer = setupSolrServer()
        setupMongo()

        val searchFuture = suggester.search("query", "testSite", new SimpleCollector[Element](), solrServer)
        val results = Await.result(searchFuture, Duration.Inf)

        there was two(solrServer).query(any[SolrQuery])

        results.elements().length must beEqualTo(4)

        results.elements()(0).id.isEmpty must beFalse
        results.elements()(0).id.get must beEqualTo("0")

        results.elements()(1).id.isEmpty must beFalse
        results.elements()(1).id.get must beEqualTo("1")

        results.elements()(2).id.isEmpty must beFalse
        results.elements()(2).id.get must beEqualTo("3")

        results.elements()(3).id.isEmpty must beFalse
        results.elements()(3).id.get must beEqualTo("2")
      }
    }

    // @todo gsegura: add argument captor to very query
    "Use query there are no userQueries in suggestions" in {
      running(FakeApplication()) {
        val suggester = new CatalogSuggester[Element]()
        val solrServer = setupSolrServer(returnUserQueries =  false)
        setupMongo()

        val searchFuture = suggester.search("query", "testSite", new SimpleCollector[Element](), solrServer)
        val results = Await.result(searchFuture, Duration.Inf)

        there was two(solrServer).query(any[SolrQuery])

        results.elements().length must beEqualTo(3)

        results.elements()(0).id.isEmpty must beFalse
        results.elements()(0).id.get must beEqualTo("0")

        results.elements()(1).id.isEmpty must beFalse
        results.elements()(1).id.get must beEqualTo("1")

        results.elements()(2).id.isEmpty must beFalse
        results.elements()(2).id.get must beEqualTo("2")
      }
    }

    "Queries product catalog if found userQueries in suggestions" in {
      running(FakeApplication()) {
        val suggester = new CatalogSuggester[Element]()
        val solrServer = setupSolrServer(returnCatalogResults = true)
        setupMongo()

        val searchFuture = suggester.search("query", "testSite", new SimpleCollector[Element](), solrServer)
        val results = Await.result(searchFuture, Duration.Inf)

        there was two(solrServer).query(any[SolrQuery])

        results.elements().length must beEqualTo(5)

        results.elements()(0).id.isEmpty must beFalse
        results.elements()(0).id.get must beEqualTo("0")

        results.elements()(1).id.isEmpty must beFalse
        results.elements()(1).id.get must beEqualTo("1")

        results.elements()(2).id.isEmpty must beFalse
        results.elements()(2).id.get must beEqualTo("3")

        //Product catalog responses should go first
        results.elements()(3).id.isEmpty must beFalse
        results.elements()(3).id.get must beEqualTo("skuId")

        results.elements()(4).id.isEmpty must beFalse
        results.elements()(4).id.get must beEqualTo("2")
      }
    }
  }

  def setupMongo() = {
    val storage = mock[MongoStorage]
    storageFactory = mock[MongoStorageFactory]
    storageFactory.getInstance(anyString) returns storage

    val brand = new Brand(Some("0"), Some("Test Brand"), Some("/brand/logo"), Some("/brand/url"), None)


    val category = Category.getInstance(Some("1"))
    category.name = Some("Test Category")
    category.seoUrlToken = Some("/category/url")

    val suggestionProduct = Product.getInstance()
    suggestionProduct.id = Some("2")

    val catalogProduct = Product.getInstance()
    catalogProduct.id = Some("skuId")
    catalogProduct.title = Some("A title that matches better")

    storage.findBrands(===(Seq("0")), any) returns Future(Seq(brand))
    storage.findCategories(===(Seq("1")), any) returns Future(Seq(category))
    storage.findProducts(===(Seq(("2", null))), any, any, any) returns Future(Seq(suggestionProduct))
    storage.findProducts(===(Seq(("productId", "skuId"))), any, any, any) returns Future(Seq(catalogProduct))
  }

  def setupSolrServer(returnUserQueries : Boolean = true, returnCatalogResults : Boolean = false) = {
    solrServer = mock[AsyncSolrServer]
    var types = Seq("brand", "category", "product", "userQuery")

    if(!returnUserQueries) {
      types = types.dropRight(1)
    }

    var count = 0
    val list = new util.ArrayList[Object]()

    types foreach { typeName =>
      val response = new SolrDocumentList()
      response.setNumFound(1)
      var doc = new SolrDocument()

      if(typeName == "userQuery") {
        doc.setField("id", count.toString)
        doc.setField("userQuery", "query suggestion")
        doc.setField("count", Int.box(20))
      }
      else {
        doc.setField("id", s"$typeName-$count")
      }

      response.add(doc)

      val groupCommand = new SimpleOrderedMap[Object]()
      groupCommand.add("groupValue", typeName)
      groupCommand.add("doclist", response)

      list.add(groupCommand)
      count = count + 1
    }

    val groupField = new SimpleOrderedMap[Object]
    groupField.add("matches", Int.box(1))
    groupField.add("groups", list)

    val groupResponse = new NamedList[Object]()
    groupResponse.add("type", groupField)

    val queryResponse = new NamedList[Object]()
    queryResponse.add("grouped", groupResponse)

    val suggestQueryResponse = new QueryResponse()
    suggestQueryResponse.setResponse(queryResponse)

    var catalogResponse : QueryResponse = null
    if(returnCatalogResults) {
      catalogResponse = setupCatalogResponse()
    }
    else {
      catalogResponse = new QueryResponse()
    }

    solrServer.query(any[SolrQuery]) returns Future(suggestQueryResponse) thenReturns Future(catalogResponse)
    solrServer
  }

  def setupCatalogResponse() = {
    val response = new SolrDocumentList()
    response.setNumFound(1)
    var doc = new SolrDocument()
    doc.setField("id", "skuId")

    response.add(doc)

    val groupCommand = new SimpleOrderedMap[Object]()
    groupCommand.add("groupValue", "productId")
    groupCommand.add("doclist", response)

    val list = new util.ArrayList[Object]()
    list.add(groupCommand)

    val groupField = new SimpleOrderedMap[Object]
    groupField.add("matches", Int.box(1))
    groupField.add("groups", list)
    val groupResponse = new NamedList[Object]()
    groupResponse.add("productId", groupField)

    val queryResponse = new NamedList[Object]()
    queryResponse.add("grouped", groupResponse)

    val catalogResponse = new QueryResponse()
    catalogResponse.setResponse(queryResponse)

    catalogResponse
  }
}
