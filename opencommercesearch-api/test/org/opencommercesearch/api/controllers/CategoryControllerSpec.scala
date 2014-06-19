package org.opencommercesearch.api.controllers

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

import play.api.libs.json.{JsError, Json}
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.models.Category
import org.opencommercesearch.api.service.{MongoStorage, MongoStorageFactory}

import org.apache.solr.client.solrj.{AsyncSolrServer, SolrRequest}
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.common.SolrDocument

import org.specs2.mutable._

import com.mongodb.WriteResult

class CategoryControllerSpec extends BaseSpec {

  val storage = mock[MongoStorage]

  trait Categories extends Before {
    def before = {
      // @todo: use di
      solrServer = mock[AsyncSolrServer]
      solrServer.binder returns mock[DocumentObjectBinder]

      storageFactory = mock[MongoStorageFactory]
      storageFactory.getInstance(anyString) returns storage
      val writeResult = mock[WriteResult]
      storage.saveCategory(any) returns Future.successful(writeResult)

      CategoryController.categoryService.server = solrServer
    }
  }

  sequential

  "Category Controller" should {

    "send 404 on an unknown route" in new Categories {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "send 404 when a category is not found" in new Categories {
      running(FakeApplication()) {
        val (queryResponse, namedList) = setupQuery
        val doc = mock[SolrDocument]
        val expectedId = "1000"

        namedList.get("doc") returns doc
        doc.get("id") returns expectedId

        storage.findCategory(anyString, any) returns Future.successful(null)

        val result = route(FakeRequest(GET, routes.CategoryController.findById(expectedId).url))
        validateQueryResult(result.get, NOT_FOUND, "application/json", s"Cannot retrieve category with id $expectedId")
      }
    }

    "send 200 when a category is found" in new Categories {
      running(FakeApplication()) {
        val (queryResponse, namedList) = setupQuery
        val doc = mock[SolrDocument]
        val (expectedId, expectedName) = ("1000", "A Category")
        val categoryB = new Category(Some("rootCategory"), Some(expectedName), None, None, None, Some(Seq("catalogB")), None, None, None)
        val categoryA = new Category(Some(expectedId), Some(expectedName), None, None, None, Some(Seq("catalogA")), None, Some(Seq(categoryB)), None)

        namedList.get("doc") returns doc
        doc.get("id") returns expectedId

        var facetFields = new java.util.LinkedList[FacetField]()
        val facetField = new FacetField("categoryPath")
        facetField.add("rootCategory", 10)
        facetField.add("1000", 10)

        facetFields.add(facetField)

        queryResponse.getResponse returns namedList
        queryResponse.getFacetFields returns facetFields

        storage.findAllCategories(any) returns Future.successful(Seq(categoryA, categoryB))

        val result = route(FakeRequest(GET, routes.CategoryController.findById(expectedId).url))
        validateQueryResult(result.get, OK, "application/json")

        val json = Json.parse(contentAsString(result.get))
        (json \ "category").validate[Category].map { category =>
          category.id.get must beEqualTo(expectedId)
        } recoverTotal {
          e => failure("Invalid JSON for category: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a category is found by site" in new Categories {
      running(FakeApplication()) {
        val (queryResponse, namedList) = setupQuery
        val doc = mock[SolrDocument]
        val (expectedId, expectedName) = ("1000", "A Category")
        val categoryA = new Category(Some(expectedId), Some(expectedName), None, None, None, Some(Seq("catalogA")), None, Some(Seq(new Category(Some("rootCategory"), None, None, None, None, None, None, None, None))), None)
        val categoryB = new Category(Some("rootCategory"), None, None, None, None, Some(Seq("catalogA")), None, None, Some(Seq(new Category(Some(expectedId), None, None, None, None, None, None, None, None))))

        namedList.get("doc") returns doc
        doc.get("id") returns expectedId

        val facetFields = new java.util.LinkedList[FacetField]()
        val facetField = new FacetField("categoryPath")
        facetField.add("1000", 10)
        facetField.add("rootCategory", 10)

        facetFields.add(facetField)

        queryResponse.getResponse returns namedList
        queryResponse.getFacetFields returns facetFields

        storage.findAllCategories(any) returns Future.successful(Seq(categoryA, categoryB))

        val result = route(FakeRequest(GET, routes.CategoryController.findBySite("catalogA").url))
        validateQueryResult(result.get, OK, "application/json")

        val json = Json.parse(contentAsString(result.get))

        (json \ "categories").validate[Seq[Category]].map { categoryList =>
          categoryList map {category =>
            category.id.get must beEqualTo(expectedId)
          }
        } recoverTotal {
          e => failure("Invalid JSON for category: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 400 when not sending a JSON body" in new Categories {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate

        val url = routes.CategoryController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "text/plain"))

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResult(result.get, BAD_REQUEST)
      }
    }

    "send 400 when exceeding maximum categories an a bulk create" in new Categories {
      running(FakeApplication(additionalConfiguration = Map("category.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedIsRuleBased) = ("1000", "A Category", true)
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "categories" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "logo" -> expectedIsRuleBased),
            Json.obj(
              "id" -> (expectedId + "1"),
              "name" -> (expectedName + " X"),
              "logo" -> expectedIsRuleBased),
            Json.obj(
              "id" -> (expectedId + "2"),
              "name" -> (expectedName + " Y"),
              "logo" -> expectedIsRuleBased)))

        val url = routes.CategoryController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Exceeded number of categories. Maximum is 2")
      }
    }

    "send 400 when trying to bulk create categories with missing fields" in new Categories {
      running(FakeApplication(additionalConfiguration = Map("categories.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedIsRuleBased) = ("1000", "A Category", false)
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "categories" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "isRuleBased" -> expectedIsRuleBased),
            Json.obj(
              "id" -> (expectedId + "2"),
              "isRuleBased" -> expectedIsRuleBased)))

        val url = routes.CategoryController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Missing required fields")
        validateFailedUpdate(updateResponse)
      }
    }

    "send 201 when a categories are created" in new Categories {
      running(FakeApplication()) {
        setupUpdate
        val (expectedId, expectedName, expectedSeoUrlToken, expectedIsRuleBased, hierarchyTokens) =
          ("1000", "A Category", "/a-category", true, Seq("1.mysite.cat1", "1.mysite.cat2"))
        val (expectedId2, expectedName2, expectedSeoUrlToken2, expectedIsRuleBased2, hierarchyTokens2) =
          ("1001", "Another Category", "/another-category", false, Seq("1.mysite.cat1", "1.mysite.cat2"))
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "categories" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "seoUrlToken" -> expectedSeoUrlToken,
              "isRuleBased" -> expectedIsRuleBased,
              "ruleFilters" -> Seq("en_US:(filter:value)"),
              "hierarchyTokens" -> hierarchyTokens),
            Json.obj(
              "id" -> expectedId2,
              "name" -> expectedName2,
              "seoUrlToken" -> expectedSeoUrlToken2,
              "isRuleBased" -> expectedIsRuleBased2,
              "hierarchyTokens" -> hierarchyTokens2)))

        val url = routes.CategoryController.bulkCreateOrUpdate().url + "?preview=false"
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)
        val result = route(fakeRequest)
        Await.ready(result.get, Duration.Inf)
        validateUpdateResult(result.get, CREATED)
        there was two(solrServer).request(any[SolrRequest]) //One for the category collection, and other for the suggestion collection
      }
    }

    "send 201 when categories are created, but don't send to autocomplete collection if only rule based categories" in new Categories {
      running(FakeApplication()) {
        setupUpdate
        val (expectedId, expectedName, expectedSeoUrlToken, expectedIsRuleBased, hierarchyTokens) =
          ("1000", "A Category", "/a-category", true, Seq("1.mysite.cat1", "1.mysite.cat2"))
        val (expectedId2, expectedName2, expectedSeoUrlToken2, expectedIsRuleBased2, hierarchyTokens2) =
          ("1001", "Another Category", "/another-category", true, Seq("1.mysite.cat1", "1.mysite.cat2"))
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "categories" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "seoUrlToken" -> expectedSeoUrlToken,
              "isRuleBased" -> expectedIsRuleBased,
              "ruleFilters" -> Seq("en_US:(filter:value)"),
              "hierarchyTokens" -> hierarchyTokens),
            Json.obj(
              "id" -> expectedId2,
              "name" -> expectedName2,
              "seoUrlToken" -> expectedSeoUrlToken2,
              "isRuleBased" -> expectedIsRuleBased2,
              "hierarchyTokens" -> hierarchyTokens2)))

        val url = routes.CategoryController.bulkCreateOrUpdate().url + "?preview=false"
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)
        val result = route(fakeRequest)
        Await.ready(result.get, Duration.Inf)
        validateUpdateResult(result.get, CREATED)
        there was one(solrServer).request(any[SolrRequest])
      }
    }
  }
}
