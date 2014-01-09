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

import play.api.mvc.SimpleResult
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.{JsError, Json, JsArray}

import scala.concurrent.Future

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.apache.solr.client.solrj.impl.AsyncCloudSolrServer
import org.apache.solr.client.solrj.{SolrRequest, SolrQuery}
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.util.NamedList
import org.apache.solr.common.{SolrDocumentList, SolrDocument}
import org.opencommercesearch.api.models.Facet

import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.opencommercesearch.api.service.{MongoStorage, MongoStorageFactory}

class FacetControllerSpec extends Specification with Mockito {

  trait Facets extends Before {
    def before = {
      solrServer = mock[AsyncCloudSolrServer]
      storageFactory = mock[MongoStorageFactory]
      storageFactory.getInstance(anyString) returns mock[MongoStorage]
    }
  }

  sequential

  "Facet Controller" should {

    "send 404 on an unknown route" in new Facets {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "send 404 on a get to all facets"  in new Facets {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/v1/facets")) must beNone
      }
    }

    "send 404 when a facet is not found"  in new Facets {
      running(FakeApplication()) {
        val (queryResponse, docList) = setupQuery
        val expectedId = "1000"

        val response = route(FakeRequest(GET, routes.FacetController.findById(expectedId).url))

        validateQuery(queryResponse, docList)
        validateQueryResult(response.get, NOT_FOUND, "application/json", s"Cannot find facet with id [$expectedId]")
      }
    }

    "send 200 when a facet is found" in new Facets {
      running(FakeApplication()) {
        val (queryResponse, docList) = setupQuery
        val doc = mock[SolrDocument]
        val (expectedId) = ("1000")

        docList.get(0) returns doc
        doc.get("id") returns expectedId
        doc.getFieldValue("id") returns expectedId

        val response = route(FakeRequest(GET, routes.FacetController.findById(expectedId).url))

        validateQuery(queryResponse, docList)
        validateQueryResult(response.get, OK, "application/json")

        val json = Json.parse(contentAsString(response.get))
        (json \ "facet").validate[Facet].map { facet =>
          facet.id.isEmpty must beEqualTo(false)
          facet.id.get must beEqualTo(expectedId)
        } recoverTotal {
          e => failure("Invalid JSON for facet: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 201 when a facet is created" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "facet1"
        val json = getFacetJson(expectedId)

        val url = routes.FacetController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(response.get, CREATED, url)
      }
    }

    "send 400 when trying to create a facet with missing fields" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val json = Json.obj(
          "id" -> expectedId
        )

        val url = routes.FacetController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "Illegal Facet fields")
      }
    }

    "send 400 when not sending a JSON body" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"

        val url = routes.FacetController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "text/plain"))

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResult(response.get, BAD_REQUEST)
      }
    }

    // @todo test bulk update
    "send 400 when exceeding maximum facets an a bulk create" in new Facets {
      running(FakeApplication(additionalConfiguration = Map("facet.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val expectedId2 = "1001"
        val expectedId3 = "1003"
        val json = Json.obj(
          "facets" -> Json.arr(
            getFacetJson(expectedId),
            getFacetJson(expectedId2),
            getFacetJson(expectedId3)))

        val url = routes.FacetController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "{\"message\":\"Exceeded number of Facets. Maximum is 2\"}")
      }
    }

    "send 400 when trying to bulk create facets with missing fields" in new Facets {
      running(FakeApplication(additionalConfiguration = Map("facet.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId) = ("1000")
        val json = Json.obj(
          "facets" -> Json.arr(
            Json.obj(
              "id" -> expectedId),
            Json.obj(
              "id" -> (expectedId + "2"))))

        val url = routes.FacetController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "Illegal Facet fields")
      }
    }

    "send 201 when facets are created" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val expectedId2 = "1001"
        val json = Json.obj(
          "facets" -> Json.arr(
            getFacetJson(expectedId),
            getFacetJson(expectedId2)))

        val url = routes.FacetController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(response.get, CREATED)

        val jsonResponse = Json.parse(contentAsString(response.get))
        val jsLocations = (jsonResponse \\ "locations").asInstanceOf[List[JsArray]]

        val expectedUrls = Set(routes.FacetController.findById(expectedId).url, routes.FacetController.findById(expectedId2).url)

        for (jsLocation <- jsLocations) {
          for (location <- jsLocation.value) {
            expectedUrls should contain (location.as[String])
          }
        }
      }
    }

    "send 200 on facets commit" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.FacetController.commitOrRollback(commit = true, rollback = false).url
        val fakeRequest = FakeRequest(POST, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "commit success")
      }
    }

    "send 200 on facets rollback" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.FacetController.commitOrRollback(commit = false, rollback = true).url
        val fakeRequest = FakeRequest(POST, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "rollback success")
      }
    }

    "send 200 on facets delete" in new Facets {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.FacetController.deleteByQuery().url
        val fakeRequest = FakeRequest(DELETE, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "delete success")
      }
    }
  }

  private def getFacetJson(id : String) = {
    Json.parse("{\"id\": \"" + id + "\"," +
                "\"type\": \"facetField\"," +
                "\"name\": \"fieldName\"}")
  }

  private def setupQuery = {
    val queryResponse = mock[QueryResponse]
    val docList = mock[SolrDocumentList]
    val binder = new DocumentObjectBinder

    queryResponse.getResults returns docList
    solrServer.query(any[SolrQuery]) returns Future.successful(queryResponse)
    solrServer.binder returns binder
    (queryResponse, docList)
  }

  private def validateQuery(queryResponse: QueryResponse, docList: SolrDocumentList) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).query(any[SolrQuery])
    there was one(queryResponse).getResults
    there was one(docList).get(0)
  }

  private def validateQueryResult(result: Future[SimpleResult], expectedStatus: Int, expectedContentType: String, expectedContent: String = null) = {
    status(result) must equalTo(expectedStatus)
    contentType(result).get must beEqualTo(expectedContentType)
    if (expectedContent != null) {
      contentAsString(result) must contain (expectedContent)
    }
  }

  private def validateUpdate(updateResponse: NamedList[AnyRef]) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).request(any[SolrRequest])
  }

  private def validateFailedUpdate(updateResponse: NamedList[AnyRef]) = {
    there was no(solrServer).request(any[SolrRequest])
  }

  private def validateUpdateResult(result: Future[SimpleResult], expectedStatus: Int, expectedLocation: String = null) = {
    status(result) must equalTo(expectedStatus)
    if (expectedLocation != null) {
      headers(result).get(LOCATION).get must endWith(expectedLocation)
    }
  }

  private def validateUpdateResultWithMessage(result: Future[SimpleResult], expectedStatus: Int, expectedContent: String) = {
    status(result) must equalTo(expectedStatus)
    contentAsString(result) must contain (expectedContent)
  }

  private def setupUpdate = {
    val updateResponse = mock[NamedList[AnyRef]]
    val binder = new DocumentObjectBinder
    solrServer.binder returns binder

    solrServer.request(any[SolrRequest]) returns Future.successful(updateResponse)
    (updateResponse)
  }
}
