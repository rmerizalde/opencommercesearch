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

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.{JsError, Json}
import scala.concurrent.{Future}
import org.specs2.mutable._
import org.apache.solr.client.solrj.AsyncSolrServer
import org.apache.solr.common.SolrDocument
import org.opencommercesearch.api.models.Brand
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.service.{MongoStorage, MongoStorageFactory}
import com.mongodb.WriteResult

class BrandControllerSpec extends BaseSpec {

   val storage = mock[MongoStorage]
      
  trait Brands extends Before {
    def before = {
      solrServer = mock[AsyncSolrServer]
      
      storageFactory = mock[MongoStorageFactory]
      storageFactory.getInstance(anyString) returns storage
      val writeResult = mock[WriteResult]
      storage.saveBrand(any) returns Future.successful(writeResult)
      
    }
  }

  sequential

  "Brand Controller" should {

    "send 404 on an unknown route" in new Brands {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "send 404 when a brand is not found"  in new Brands {
      running(FakeApplication()) {
        val (queryResponse, namedList) = setupQuery
        val expectedId = "1000"

        val result = route(FakeRequest(GET, routes.BrandController.findById(expectedId).url))
        validateQuery(queryResponse, namedList)
        validateQueryResult(result.get, NOT_FOUND, "application/json", s"Cannot find brand with id [$expectedId]")
      }
    }

    "send 200 when a brand is found" in new Brands {
      running(FakeApplication()) {
        val (queryResponse, namedList) = setupQuery
        val doc = mock[SolrDocument]
        val (expectedId, expectedName, expectedLogo) = ("1000", "A Brand", "/brands/logo.jpg")

        namedList.get("doc") returns doc
        doc.get("id") returns expectedId
        doc.get("name") returns expectedName
        doc.get("logo") returns expectedLogo

        val result = route(FakeRequest(GET, routes.BrandController.findById(expectedId).url))
        validateQuery(queryResponse, namedList)
        validateQueryResult(result.get, OK, "application/json")

        val json = Json.parse(contentAsString(result.get))
        (json \ "brand").validate[Brand].map { brand =>
          brand.id.get must beEqualTo(expectedId)
          brand.name.get must beEqualTo(expectedName)
          brand.logo.get must beEqualTo(expectedLogo)
        } recoverTotal {
          e => failure("Invalid JSON for brand: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 201 when a brand is created" in new Brands {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedLogo) = ("1000", "A Brand", "/brands/logo.jpg")
        val json = Json.obj(
          "id" -> expectedId,
          "name" -> expectedName,
          "logo" -> expectedLogo
        )

        val url = routes.BrandController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(result.get, CREATED, url)
      }
    }

    "send 400 when trying to create a brand with missing fields" in new Brands {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val json = Json.obj(
          "id" -> expectedId
        )

        val url = routes.BrandController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Missing required fields")
      }
    }

    "send 400 when not sending a JSON body" in new Brands {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"

        val url = routes.BrandController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "text/plain"))

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResult(result.get, BAD_REQUEST)
      }
    }

    "send 400 when exceeding maximum brands an a bulk create" in new Brands {
      running(FakeApplication(additionalConfiguration = Map("brand.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedLogo) = ("1000", "A Brand", "/brands/logo.jpg")
        val json = Json.obj(
          "feedTimestamp" -> 1000,
          "brands" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "logo" -> expectedLogo),
            Json.obj(
              "id" -> (expectedId + "1"),
              "name" -> (expectedName + " X"),
              "logo" -> expectedLogo),
            Json.obj(
              "id" -> (expectedId + "2"),
              "name" -> (expectedName + " Y"),
              "logo" -> expectedLogo)))

        val url = routes.BrandController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Exceeded number of brands. Maximum is 2")
      }
    }

    "send 400 when trying to bulk create brands with missing fields" in new Brands {
      running(FakeApplication(additionalConfiguration = Map("brand.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedLogo) = ("1000", "A Brand", "/brands/logo.jpg")
        val json = Json.obj(
          "brands" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "logo" -> expectedLogo),
            Json.obj(
              "id" -> (expectedId + "2"),
              "logo" -> expectedLogo)))

        val url = routes.BrandController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateFailedUpdateResult(result.get, BAD_REQUEST, "Missing required fields")
        validateFailedUpdate(updateResponse)
      }
    }

    "send 201 when a brands are created" in new Brands {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val (expectedId, expectedName, expectedLogo) = ("1000", "A Brand", "/brands/logo.jpg")
        val (expectedId2, expectedName2, expectedLogo2) = ("1001", "Another Brand", "/brands/logo2.jpg")
        val json = Json.obj(
          "feedTimestamp" -> 1000,
          "brands" -> Json.arr(
            Json.obj(
              "id" -> expectedId,
              "name" -> expectedName,
              "logo" -> expectedLogo),
            Json.obj(
              "id" -> expectedId2,
              "name" -> expectedName2,
              "logo" -> expectedLogo2)))

        val url = routes.BrandController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val result = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(result.get, CREATED)
      }
    }
  }
}
