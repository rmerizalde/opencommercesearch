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
import org.opencommercesearch.api.models.Product
import play.api.i18n.Lang
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Await}
import play.api.test.FakeApplication
import play.api.test.Helpers._
import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}
import org.opencommercesearch.api.Global._
import  ExecutionContext.Implicits.global
import org.apache.solr.client.solrj.response.QueryResponse
import org.opencommercesearch.search.Element

import org.opencommercesearch.search.collector.SimpleCollector
import org.apache.solr.common.util.{SimpleOrderedMap, NamedList}
import org.apache.solr.common.{SolrDocument, SolrDocumentList}
import java.util
import org.opencommercesearch.api.service.{MongoStorageFactory, MongoStorage}
import org.opencommercesearch.api.models.{Category, Brand}

/**
 * @author jmendez
 */
class MultiSuggesterSpec extends Specification with Mockito {
  implicit val context = Context(preview = true, Lang("en"))

  "MultiSuggester" should {

    "Init suggesters from config" in {
      val suggesterClasses = "org.opencommercesearch.search.suggester.TestSuggester1, " +
                             "\torg.opencommercesearch.search.suggester.TestSuggester2," +
                             "  org.opencommercesearch.search.suggester.TestSuggester3" //If a class does not exist, other classes should still be initialized

      running(FakeApplication(additionalConfiguration = Map("suggester.extra" -> suggesterClasses))) {
        val suggester = new MultiSuggester()

        suggester.sources().contains("Suggester1") must beTrue
        suggester.sources().contains("Suggester2") must beTrue
        suggester.sources().contains("Suggester3") must beFalse //as Suggester3 class does not exist
      }
    }

    "Return results from multiple sources" in {
      running(FakeApplication()) {
        val suggester = new MultiSuggester()
        val solrServer = setupSolrServer()
        val storage = setupMongo()

        storage.findProducts(any[Seq[(String, String)]], any[String], any[Seq[String]], any[Boolean]) returns Future.successful(Iterable.empty[Product])

        val searchFuture = suggester.search("query", "testSite", new SimpleCollector[Element](), solrServer)
        val results = Await.result(searchFuture, Duration.Inf)

        results.elements().length must beEqualTo(2)

        val brand = results.elements()(0)
        brand.id.isEmpty must beFalse
        brand.id.get must beEqualTo("00")
        val category = results.elements()(1)
        category.id.isEmpty must beFalse
        category.id.get must beEqualTo("01")
      }
    }

    "Returns results even if a source failed" in {
      val suggesterClasses = "org.opencommercesearch.search.suggester.TestSuggester1" //An error on a given source, should not prevent the multi suggester from returning other sources elements

      running(FakeApplication(additionalConfiguration = Map("suggester.extra" -> suggesterClasses))) {
        val suggester = new MultiSuggester()
        val solrServer = setupSolrServer()
        val storage = setupMongo()

        storage.findProducts(any[Seq[(String, String)]], any[String], any[Seq[String]], any[Boolean]) returns Future.successful(Iterable.empty[Product])

        val searchFuture = suggester.search("query", "testSite", new SimpleCollector[Element](), solrServer)
        val results = Await.result(searchFuture, Duration.Inf)

        results.elements().length must beEqualTo(2)

        val brand = results.elements()(0)
        brand.id.isEmpty must beFalse
        brand.id.get must beEqualTo("00")
        val category = results.elements()(1)
        category.id.isEmpty must beFalse
        category.id.get must beEqualTo("01")
      }
    }
  }

  def setupMongo() = {
    val storage = mock[MongoStorage]
    storageFactory = mock[MongoStorageFactory]
    storageFactory.getInstance(anyString) returns storage

    val brand = new Brand(Some("00"), Some("Test Brand"), Some("/brand/logo"), Some("/brand/url"), None)

    val category = Category.getInstance(Some("01"))
    category.name = Some("Test Category")
    category.seoUrlToken = Some("/category/url")

    storage.findBrands(===(Seq("00")), any) returns Future(Seq(brand))
    storage.findCategories(===(Seq("01")), any) returns Future(Seq(category))
    storage
  }

  def setupSolrServer() = {
    solrServer = mock[AsyncSolrServer]
    val suggestQueryResponse = new QueryResponse()
    val queryResponse = new NamedList[Object]()

    val doc = new NamedList[Object]()
    doc.add("id", "element0")

    val brandResponse = new SolrDocumentList()
    brandResponse.setStart(0)
    brandResponse.setNumFound(1)
    var doc1 = new SolrDocument()
    doc1.setField("id", "brand-00")
    brandResponse.add(doc1)

    val brandGroupCommand = new SimpleOrderedMap[Object]()
    brandGroupCommand.add("groupValue", "brand")
    brandGroupCommand.add("doclist", brandResponse)

    val categoryResponse = new SolrDocumentList()
    categoryResponse.setStart(0)
    categoryResponse.setNumFound(1)
    doc1 = new SolrDocument()
    doc1.setField("id", "category-01")
    categoryResponse.add(doc1)

    val categoryGroupCommand = new SimpleOrderedMap[Object]()
    categoryGroupCommand.add("groupValue", "category")
    categoryGroupCommand.add("doclist", categoryResponse)

    val groupField = new SimpleOrderedMap[Object]
    groupField.add("matches", Int.box(1))
    val list = new util.ArrayList[Object]()
    list.add(brandGroupCommand)
    list.add(categoryGroupCommand)
    groupField.add("groups", list)
    val groupResponse = new NamedList[Object]()
    groupResponse.add("type", groupField)
    queryResponse.add("grouped", groupResponse)

    suggestQueryResponse.setResponse(queryResponse)

    solrServer.query(any[SolrQuery]) returns Future(suggestQueryResponse)
    solrServer
  }
}

class TestSuggester1 extends Suggester[Element] {
  override def sources(): Set[String] = Set("Suggester1")

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[Element]] = {
    throw new UnsupportedOperationException()
  }
}

class TestSuggester2 extends Suggester[Element] {
  override def sources(): Set[String] = Set("Suggester2")

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[Element]] = {
    throw new UnsupportedOperationException()
  }
}
