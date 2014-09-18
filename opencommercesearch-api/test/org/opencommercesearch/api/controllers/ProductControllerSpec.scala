package org.opencommercesearch.api.controllers

import play.api.libs.json.{JsError, Json}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._

import scala.concurrent.Future

import java.util

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.models._
import org.opencommercesearch.api.service.{MongoStorage, MongoStorageFactory}

import org.apache.solr.client.solrj.{AsyncSolrServer, SolrQuery}
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.apache.solr.client.solrj.response._
import org.apache.solr.common.{SolrDocument, SolrDocumentList}
import org.apache.solr.common.util.NamedList

import org.junit.runner.RunWith
import org.specs2.mutable.Before
import org.specs2.runner.JUnitRunner

import com.mongodb.WriteResult

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
@RunWith(classOf[JUnitRunner])
class ProductControllerSpec extends BaseSpec {

  trait Products extends Before {
    def before = {
      // @todo: use di
      solrServer = mock[AsyncSolrServer]
      solrServer.binder returns mock[DocumentObjectBinder]
      storageFactory = mock[MongoStorageFactory]
      val storage = mock[MongoStorage]
      storageFactory.getInstance(anyString) returns storage
      ProductController.categoryService.storageFactory = storageFactory
      val writeResult = mock[WriteResult]
      storage.saveProduct(any) returns Future.successful(writeResult)

      val category = Category.getInstance(Some("someCategory"))
      category.isRuleBased = Option(false)
      category.sites = Option(Seq("mysite"))
      category.hierarchyTokens = Option(Seq("2.mysite.category.subcategory"))
      storage.findCategory(any, any) returns Future.successful(category)

      val facet = Facet.getInstance()
      facet.id = Some("facetId")
      facet.blackList = Some(Seq.empty[String])
      storage.findFacets(any, any) returns Future.successful(Seq(facet))
    }
  }

  def fakeApplication() = FakeApplication(additionalConfiguration = Map("sites.closeout" -> "mysite"))
  sequential

  "Product Controller" should {

    "send 404 when a product is not found"  in new Products {
      running(FakeApplication()) {
        val expectedId = "PRD1000"

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any, any) returns Future.successful(null)
        storage.findProducts(any, any, any, any) returns Future.successful(null)

        val result = route(FakeRequest(GET, routes.ProductController.findById(expectedId, "mysite").url))
        validateQueryResult(result.get, NOT_FOUND, "application/json", s"Cannot find products with ids [$expectedId]")
      }
    }

    "send 200 when a product is found when searching by id" in new Products {
      running(FakeApplication()) {
        val facetResponse = setupAncestorCategoryQuery()

        solrServer.query(any[SolrQuery]) answers { q =>
          Future.successful(facetResponse)
        }

        val product = Product.getInstance()
        val (expectedId, expectedTitle) = ("PRD1000", "A Product")

        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any, any) returns Future.successful(Seq(product))
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val category = Category.getInstance(Some("someCategory"))
        category.sites = Option(Seq("mysite"))

        storage.findAllCategories(any) returns Future.successful(Seq(category))

        val result = route(FakeRequest(GET, routes.ProductController.findById(expectedId, "mysite").url))
        validateQueryResult(result.get, OK, "application/json")
        val json = Json.parse(contentAsString(result.get))
        (json \ "products").as[Seq[Product]].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              sku.id.get must beEqualTo(expectedSku.id.get)
            }
          }

          val categories = product.categories
          categories.size must beEqualTo(1)
          categories.get.size must beEqualTo(1)
          categories.get(0).getId must beEqualTo("someCategory")
        }
      }
    }

    "send 200 when a product is found when searching by query that requires partial matching" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        product.id = Some("PRD1000")
        product.title = Some("A Product")
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        
        var productQuery:SolrQuery = null
        val skuResponseEmpty = setupGroupQuery(Seq.empty)
        val skuResponseValid = setupGroupQuery(Seq(product))
        
        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))
        
        solrServer.query(any[SolrQuery]) returns Future.successful(skuResponseEmpty) thenReturn Future.successful(skuResponseValid)
        
        val result = route(FakeRequest(GET, routes.ProductController.search("term to partial match", "mysite").url))
        val json = Json.parse(contentAsString(result.get))
        (json \ "metadata" \ "found" ).as[Int]  must beEqualTo(1)
        (json \ "metadata" \ "spellCheck" \ "correctedTerms").as[String]  must beEqualTo("term to partial match")
        (json \ "metadata" \ "spellCheck" \ "similarResults").as[Boolean]  must beEqualTo(true)
        
      }
    }
    
    "send 200 when a product is found when searching by query" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.search("term", "mysite").url))
        validateQueryResult(result.get, OK, "application/json")
        validateCommonQueryParams(productQuery)
        validateSearchParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when searching by query with sort options" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.search("term", "mysite").url + "&sort=discountPercent desc"))
        validateQueryResult(result.get, OK, "application/json")
        productQuery.getQuery must beEqualTo("term")
        validateCommonQueryParams(productQuery, expectedGroupSorting = true)
        validateSearchParams(productQuery)

        productQuery.get("sort") must beEqualTo("discountPercentUS desc")

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when browsing a category" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.browse("mysite", "cat1", outlet = false).url))
        validateQueryResult(result.get, OK, "application/json")
        validateBrowseQueryParams(productQuery, "mysite", "cat1", null, isOutlet = false, escapedCategoryFilter = "2.mysite.category.subcategory")
        validateCommonQueryParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when browsing a rule based category" in new Products {
      running(fakeApplication()) {

        val category = Category.getInstance(Some("someCategory"))
        category.isRuleBased = Option(true)
        category.hierarchyTokens = Option(Seq("1.mysite.cat1"))
        category.ruleFilters = Option(Seq("en_US:(category:cat1 AND category:cat2)", "fr_CA:(category:cat1)"))
        val storage = storageFactory.getInstance("namespace")
        storage.findCategory(any, any) returns Future.successful(null)
        storage.findCategory(any, any) returns Future.successful(category)

        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.browse("mysite", "cat1", outlet = false).url))
        validateQueryResult(result.get, OK, "application/json")
        validateBrowseQueryParams(productQuery, "mysite", "cat1", null, isOutlet = false, ruleFilter = "category:cat1 AND category:cat2", escapedCategoryFilter = "1.mysite.cat1")
        validateCommonQueryParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when browsing an outlet category" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.browse("mysite", "cat1", outlet = true).url))
        validateQueryResult(result.get, OK, "application/json")
        validateBrowseQueryParams(productQuery, "mysite", "cat1", null, isOutlet = true, escapedCategoryFilter = "2.mysite.category.subcategory")
        validateCommonQueryParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when browsing a brand category" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.browseBrandCategory("mysite", "brand1", "cat1").url))
        validateQueryResult(result.get, OK, "application/json")
        validateBrowseQueryParams(productQuery, "mysite", "cat1", "brand1", isOutlet = false, escapedCategoryFilter = "2.mysite.category.subcategory")
        validateCommonQueryParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 200 when a product is found when browsing a brand" in new Products {
      running(fakeApplication()) {
        val product = Product.getInstance()
        val sku = Sku.getInstance()
        product.skus = Some(Seq(sku))
        val skuResponse = setupGroupQuery(Seq(product))
        var productQuery:SolrQuery = null

        val (expectedId, expectedTitle) = ("PRD1000", "A Product")
        product.id = Some(expectedId)
        product.title = Some(expectedTitle)

        val expectedSku = Sku.getInstance()
        expectedSku.id = Some("PRD1000-BLK-ONESIZE")
        product.skus = Some(Seq(expectedSku))

        solrServer.query(any[SolrQuery]) answers { q =>
          productQuery = q.asInstanceOf[SolrQuery]
          Future.successful(skuResponse)
        }

        val storage = storageFactory.getInstance("namespace")
        //scenario were we have only a brandId, so this api will return null
        storage.findCategory(any, any) returns Future.successful(null)
        storage.findProducts(any, any, any, any) returns Future.successful(Seq(product))

        val result = route(FakeRequest(GET, routes.ProductController.browseBrand("mysite", "brand1").url))
        validateQueryResult(result.get, OK, "application/json")
        validateBrowseQueryParams(productQuery, "mysite", null, "brand1", isOutlet = false, escapedCategoryFilter = "2.mysite.category.subcategory")
        validateCommonQueryParams(productQuery)

        val json = Json.parse(contentAsString(result.get))
        (json \ "product").validate[Product].map { product =>
          for (skus <- product.skus) {
            for (sku <- skus) {
              product.id.get must beEqualTo(expectedSku.id.get)
            }
          }
        } recoverTotal {
          e => failure("Invalid JSON for product: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 400 when not sending a JSON body" in new Products {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate

        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "text/plain"))

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResult(result.get, BAD_REQUEST)
      }
    }

    "send 400 when exceeding maximum products an a bulk create" in new Products {
      running(FakeApplication(additionalConfiguration = Map("index.product.batchsize.max" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName) = ("PRD100", "A Product")
        val jsonBrand = Json.obj("id" -> "1000")
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "products" -> Json.arr(
            Json.obj(
              "id" -> (expectedId + "0"),
              "title" -> expectedName,
              "brand" -> jsonBrand,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "image" -> "/images/black.jpg",
                "isRetail" -> true,
                "isCloseout" -> false,
                "countries" -> Seq(Json.obj("code" -> "US"))
              ))
            ),
            Json.obj(
              "id" -> (expectedId + "1"),
              "title" -> (expectedName + " X"),
              "brand" -> jsonBrand,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "1" + "RED"),
                "image" -> "/images/red.jpg",
                "isRetail" -> true,
                "isCloseout" -> false,
                "countries" -> Seq(Json.obj("code" -> "US"))
              ))
            ),
            Json.obj(
              "id" -> (expectedId + "2"),
              "title" -> (expectedName + " Y"),
              "brand" -> jsonBrand,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "2" + "BLU"),
                "image" -> "/images/blue.jpg",
                "isRetail" -> true,
                "isCloseout" -> false,
                "countries" -> Seq(Json.obj("code" -> "US"))
              ))
            )
          )
        )

        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Exceeded number of products. Maximum is 2")
      }
    }

    "send 201 when trying to bulk create POOS products with missing sku fields" in new Products {
      running(FakeApplication(additionalConfiguration = Map("index.product.batchsize.max" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedTitle) = ("PRD0001", "A Product")
        val jsonBrand = Json.obj("id" -> "1000")
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "products" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "title" -> expectedTitle,
              "brand" -> jsonBrand,
              "isOem" -> false,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "countries" -> Json.arr(Json.obj(
                  "availability" -> Json.obj("status" -> "PermanentlyOutOfStock")
                ))
              ))),
            Json.obj(
              "id" -> (expectedId + "2"),
              "title" -> expectedTitle,
              "brand" -> jsonBrand,
              "isOem" -> false,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "countries" -> Json.arr(Json.obj(
                  "availability" -> Json.obj("status" -> "PermanentlyOutOfStock")
                ))
              )))))

        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateUpdateResult(result.get, CREATED)
      }
    }

    "send 400 when trying to bulk create products with missing sku fields" in new Products {
      running(FakeApplication(additionalConfiguration = Map("index.product.batchsize.max" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedTitle) = ("PRD0001", "A Product")
        val jsonBrand = Json.obj("id" -> "1000")
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "products" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "title" -> expectedTitle,
              "brand" -> jsonBrand,
              "isOem" -> false,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "countries" -> Json.arr(Json.obj(
                  "availability" -> Json.obj("status" -> "InStock")
                ))
              ))),
            Json.obj(
              "id" -> (expectedId + "2"),
              "title" -> expectedTitle,
              "brand" -> jsonBrand,
              "isOem" -> false,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "countries" -> Json.arr(Json.obj(
                  "availability" -> Json.obj("status" -> "InStock")
                ))
              )))))

        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Missing required fields")
        validateFailedUpdate(updateResponse)
      }
    }

    "send 400 when trying to bulk create products with missing product fields" in new Products {
      running(FakeApplication(additionalConfiguration = Map("index.product.batchsize.max" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedTitle) = ("PRD0001", "A Product")
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "products" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "title" -> expectedTitle,
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK")
              ))),
            Json.obj(
              "id" -> (expectedId + "2"),
              "title" -> expectedTitle,
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK")
              )))))

        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Missing required fields for product")
        validateFailedUpdate(updateResponse)
      }
    }

    "send 201 when a products are created" in new Products {
      running(FakeApplication()) {
        setupUpdate
        val (expectedId, expectedName) = ("PRD0001", "A Product")
        val jsonBrand = Json.obj("id" -> "PRD0001")
        val json = Json.obj(
          "feedTimestamp" -> 1001,
          "products" -> Json.arr(
            Json.obj(
              "id" -> (expectedId + "0"),
              "title" -> expectedName,
              "brand" -> jsonBrand,
              "isOem" -> false,
              "listRank" -> 1,
              "activationDate" -> "2010-07-31T00:00:00Z",
              "skus" -> Json.arr(Json.obj(
                "id" -> (expectedId + "0" + "BLK"),
                "image" -> "/images/black.jpg",
                "isRetail" -> true,
                "isCloseout" -> false,
                "countries" -> Seq(Json.obj("code" -> "US"))
              ))
            )))


        val url = routes.ProductController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateUpdateResult(result.get, CREATED)
      }
    }
  }

  "send 201 when a OEM products are created" in new Products {
    running(FakeApplication()) {
      setupUpdate
      val (expectedId, expectedName) = ("PRD0001", "A Product")
      val jsonBrand = Json.obj("id" -> "PRD0001")
      val json = Json.obj(
        "feedTimestamp" -> 1001,
        "products" -> Json.arr(
          Json.obj(
            "id" -> (expectedId + "0"),
            "title" -> expectedName,
            "brand" -> jsonBrand,
            "isOem" -> true,
            "listRank" -> 1,
            "activationDate" -> "2010-07-31T00:00:00Z",
            "skus" -> Json.arr(Json.obj(
              "id" -> (expectedId + "0" + "BLK"),
              "image" -> "/images/black.jpg",
              "isRetail" -> true,
              "isCloseout" -> false,
              "countries" -> Seq(Json.obj("code" -> "US"))
            ))
          )))


      val url = routes.ProductController.bulkCreateOrUpdate().url
      val fakeRequest = FakeRequest(PUT, url)
        .withHeaders((CONTENT_TYPE, "application/json"))
        .withJsonBody(json)

      val result = route(fakeRequest)
      validateUpdateResult(result.get, CREATED)
    }
  }


  /**
   * Helper method to validate search parameters
   * @param productQuery
   */
  def validateSearchParams(productQuery: SolrQuery) {
    productQuery.getQuery must beEqualTo("term")
    productQuery.getBool("rule") must beEqualTo(true)
    productQuery.getFilterQueries contains "isRetail:true" must beTrue
    productQuery.getFilterQueries contains "isCloseout:false" must beFalse
    productQuery.getFilterQueries contains "isOutlet:false" must beFalse
    productQuery.getFilterQueries contains "isCloseout:true" must beFalse
    productQuery.getFilterQueries contains "isOutlet:true" must beFalse
    productQuery.getFilterQueries contains "country:onsaleUS" must beFalse
    productQuery.get("pageType") must beEqualTo("search")
  }

  /**
   * Helper method to validate common browse params
   * @param productQuery the query
   * @param categoryId an optional category id
   * @param brandId and optional brand id
   * @param ruleFilter and optional string to that represents the filter of a rule based category
   */
  private def validateBrowseQueryParams(productQuery: SolrQuery, site: String, categoryId: String, brandId: String,
                                        isOutlet: Boolean, ruleFilter: String = null, escapedCategoryFilter: String) : Unit = {
    var expected = 3
    var isRuleCategory = false
    
    productQuery.get("pageType") must beEqualTo("category")

    if (ruleFilter == null) {
      //scenario for non rule based categories
      if (categoryId != null) {
        expected += 2
        productQuery.getFilterQueries contains s"ancestorCategoryId:$categoryId" must beTrue
        productQuery.getFilterQueries contains "category:" + escapedCategoryFilter must beTrue
      }

      if (brandId != null) {
        expected += 1
        productQuery.getFilterQueries contains s"brandId:$brandId" must beTrue
        productQuery.get("brandId") must beEqualTo(brandId)
      }
    } else {
      //scenario for rule based categories
      isRuleCategory = true
      productQuery.getFilterQueries contains ruleFilter
      productQuery.getBool("rulePage") must beEqualTo(true)
      productQuery.get("q") must beEqualTo("*:*")
    }
    
    if (categoryId != null) {
        productQuery.get("categoryFilter") must beEqualTo(escapedCategoryFilter)
    }
                
    productQuery.getBool("rule") must beEqualTo(true)
    productQuery.get("siteId") must beEqualTo(site)
    productQuery.getFilterQueries.size must beEqualTo(expected)
    productQuery.getFilterQueries contains "country:US" must beTrue
    productQuery.getFilterQueries contains "isRetail:true" must beTrue
    if (!isRuleCategory) { //rule base categoris won't filter by isOutlet
      productQuery.getFilterQueries contains s"isOutlet:$isOutlet" must beTrue
    }
  }

  /**
   * Helper method to validate common query params
   * @param query the query
   */
  private def validateCommonQueryParams(query: SolrQuery, expectedGroupSorting: Boolean = true) : Unit = {
    query.getBool("facet") must beEqualTo(true)
    query.getBool("group") must beEqualTo(true)
    query.getBool("group.ngroups") must beEqualTo(true)
    query.getInt("group.limit") must beEqualTo(50)
    query.get("group.field") must beEqualTo("productId")
    query.getBool("group.facet") must beEqualTo(false)
    if (expectedGroupSorting) {
      query.get("group.sort") must beEqualTo("isCloseout asc, salePriceUS asc, sort asc, score desc")
    } else {
      query.get("group.sort") must beNull
    }
    query.getBool("groupcollapse") must beEqualTo(true)
    query.get("groupcollapse.fl") must beEqualTo("listPriceUS,salePriceUS,discountPercentUS,color,colorFamily")
    query.get("groupcollapse.ff") must beEqualTo("isCloseout")
    val facetFields = query.getFacetFields
    facetFields.size must beEqualTo(1)
    facetFields(0) must beEqualTo("category")
  }

  /**
   * Helper method to mock the response for findById calls
   * @return a query response mock
   */
  protected def setupAncestorCategoryQuery() = {
    val queryResponse = mock[QueryResponse]
    val solrDocument = mock[SolrDocument]

    val categoryValues = new java.util.ArrayList[AnyRef]()
    categoryValues.add("someCategory")

    solrDocument.getFieldValues("ancestorCategoryId") returns categoryValues
    solrDocument.containsKey("ancestorCategoryId") returns true

    val solrDocuments = new SolrDocumentList
    solrDocuments.add(solrDocument)
    queryResponse.getResults returns solrDocuments

    queryResponse
  }

  /**
   * Helper method to mock the response for findById calls
   *  @param product the product & skus use to mock the response
   * @return a query response mock
   */
  protected def setupSkuQuery(product: Product) : QueryResponse = {
    val queryResponse = mock[QueryResponse]
    val documentList = new SolrDocumentList

    queryResponse.getResults returns documentList
    solrServer.query(any[SolrQuery]) returns Future.successful(queryResponse)

    if (product != null) {
      for (skus <- product.skus) {
        for (sku <- skus) {
          val doc = mock[SolrDocument]
          documentList.add(doc)

          val prod = mock[Product]
          solrServer.binder.getBean(classOf[Product], documentList.get(0)) returns prod
          solrServer.binder.getBean(classOf[Sku], documentList.get(0)) returns sku
          prod.skus returns Some(Seq(sku))
        }
      }
    }
    queryResponse
  }

  /**
   * Helper method to mock a group response
   * @param products is the list of products used to build the mock response
   * @return
   */
  private def setupGroupQuery(products: Seq[Product]) = {
    val queryResponse = mock[QueryResponse]

    solrServer.query(any[SolrQuery]) returns Future.successful(queryResponse)

    val groupCommand = mock[GroupCommand]
    val commandValues = spy(new util.ArrayList[GroupCommand])

    commandValues.add(groupCommand)
    groupCommand.getName returns "productId"
    groupCommand.getNGroups returns products.size

    val groupValues = spy(new util.ArrayList[Group](products.size))
    groupCommand.getValues returns groupValues

    for (product <- products) {
      val group = mock[Group]
      val documentList = new SolrDocumentList

      for (skus <- product.skus) {
        val skuList = new util.ArrayList[Sku](skus.size)
        for (sku <- skus) {
          documentList.add(mock[SolrDocument])
          skuList.add(sku)
        }
      }
      group.getResult returns documentList
      groupValues.add(group)
    }

    val summary = mock[NamedList[Object]]
    val groupSummary = mock[NamedList[Object]]
    summary.get("groups_summary").asInstanceOf[NamedList[Object]] returns groupSummary

    groupSummary.get("productId").asInstanceOf[NamedList[Object]] returns null

    val groupResponse = mock[GroupResponse]

    groupResponse.getValues returns commandValues
    queryResponse.getGroupResponse returns groupResponse
    queryResponse.getResponse returns summary
    queryResponse
  }
}
