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
import org.opencommercesearch.api.models.Rule

import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.opencommercesearch.api.service.{MongoStorage, MongoStorageFactory}

class RuleControllerSpec extends Specification with Mockito {

  trait Rules extends Before {
    def before = {
      solrServer = mock[AsyncCloudSolrServer]
      storageFactory = mock[MongoStorageFactory]
      storageFactory.getInstance(anyString) returns mock[MongoStorage]
    }
  }

  sequential

  "Rule Controller" should {

    "send 404 on an unknown route" in new Rules {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "send 404 on a get to all rules"  in new Rules {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/v1/rules")) must beNone
      }
    }

    "send 404 when a rule is not found"  in new Rules {
      running(FakeApplication()) {
        val (queryResponse, docList) = setupQuery
        val expectedId = "1000"

        val response = route(FakeRequest(GET, routes.RuleController.findById(expectedId).url))

        validateQuery(queryResponse, docList, isEmpty = true)
        validateQueryResult(response.get, NOT_FOUND, "application/json", s"Cannot find rule with id [$expectedId]")
      }
    }

    "send 200 when a rule is found" in new Rules {
      running(FakeApplication()) {
        val (queryResponse, docList) = setupQuery
        val doc = mock[SolrDocument]
        val (expectedId) = ("1000")

        docList.get(0) returns doc
        docList.getNumFound returns 1
        doc.get("id") returns expectedId
        doc.getFieldValue("id") returns expectedId

        val response = route(FakeRequest(GET, routes.RuleController.findById(expectedId).url))

        validateQuery(queryResponse, docList, isEmpty = false)
        validateQueryResult(response.get, OK, "application/json")

        val json = Json.parse(contentAsString(response.get))
        (json \ "rule").validate[Rule].map { rule =>
          rule.id.isEmpty must beEqualTo(false)
          rule.id.get must beEqualTo(expectedId)
        } recoverTotal {
          e => failure("Invalid JSON for rule: " + JsError.toFlatJson(e))
        }
      }
    }

    "send 201 when a rule is created" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "rule1"
        val json = getRuleJson(expectedId)

        val url = routes.RuleController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(response.get, CREATED, url)
      }
    }

    "send 400 when trying to create a rule with missing fields" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val json = Json.obj(
          "id" -> expectedId
        )

        val url = routes.RuleController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "Illegal Rule fields")
      }
    }

    "send 400 when not sending a JSON body" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"

        val url = routes.RuleController.createOrUpdate(expectedId).url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "text/plain"))

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResult(response.get, BAD_REQUEST)
      }
    }

    // @todo test bulk update
    "send 400 when exceeding maximum rules an a bulk create" in new Rules {
      running(FakeApplication(additionalConfiguration = Map("rule.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val expectedId2 = "1001"
        val expectedId3 = "1002"
        val json = Json.obj(
          "rules" -> Json.arr(
            getRuleJson(expectedId),
            getRuleJson(expectedId2),
            getRuleJson(expectedId3)))


        val url = routes.RuleController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "{\"message\":\"Exceeded number of Rules. Maximum is 2\"}")
      }
    }

    "send 400 when trying to bulk create rules with missing fields" in new Rules {
      running(FakeApplication(additionalConfiguration = Map("rule.maxUpdateBatchSize" -> 2))) {
        val (updateResponse) = setupUpdate
        val (expectedId) = ("1000")
        val json = Json.obj(
          "rules" -> Json.arr(
            Json.obj(
              "id" -> expectedId),
            Json.obj(
              "id" -> (expectedId + "2"))))

        val url = routes.RuleController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateFailedUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, BAD_REQUEST, "Illegal Rule fields")
      }
    }

    "send 201 when rules are created" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val expectedId = "1000"
        val expectedId2 = "1001"
        val json = Json.obj(
          "rules" -> Json.arr(
            getRuleJson(expectedId),
            getRuleJson(expectedId2)))

        val url = routes.RuleController.bulkCreateOrUpdate().url
        val fakeRequest = FakeRequest(PUT, url)
          .withHeaders((CONTENT_TYPE, "application/json"))
          .withJsonBody(json)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResult(response.get, CREATED)

        val jsonResponse = Json.parse(contentAsString(response.get))
        val jsLocations = (jsonResponse \\ "locations").asInstanceOf[List[JsArray]]

        val expectedUrls = Set(routes.RuleController.findById(expectedId).url, routes.RuleController.findById(expectedId2).url)

        for (jsLocation <- jsLocations) {
          for (location <- jsLocation.value) {
            expectedUrls should contain (location.as[String])
          }
        }
      }
    }

    "send 200 on rules commit" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.RuleController.commitOrRollback(commit = true, rollback = false).url
        val fakeRequest = FakeRequest(POST, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "commit success")
      }
    }

    "send 200 on rules rollback" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.RuleController.commitOrRollback(commit = false, rollback = true).url
        val fakeRequest = FakeRequest(POST, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "rollback success")
      }
    }

    "send 200 on rules delete" in new Rules {
      running(FakeApplication()) {
        val (updateResponse) = setupUpdate
        val url = routes.RuleController.deleteByQuery().url
        val fakeRequest = FakeRequest(DELETE, url)

        val response = route(fakeRequest)
        validateUpdate(updateResponse)
        validateUpdateResultWithMessage(response.get, OK, "delete success")
      }
    }
  }

  private def getRuleJson(id : String) = {
    //id, startDate, category, siteID, sortPriority, query, catalogId, brandId, experimental, target, subTarget, endDate, combineMode
    Json.parse("{\"id\": \"" + id + "\"," +
                "\"startDate\": \"2013-06-21T16:30:22Z\"," +
                "\"category\": [\"__all__\"]," +
                "\"siteId\": [\"1\"]," +
                "\"sortPriority\": 0," +
                "\"query\": \"[purses on sale]\"," +
                "\"catalogId\": [\"__all__\"]," +
                "\"brandId\": [\"__all__\"]," +
                "\"experimental\": false," +
                "\"target\": [\"searchpages\"]," +
                "\"subTarget\": [\"__all__\"]," +
                "\"endDate\": \"2014-08-05T09:42:38Z\"," +
                "\"combineMode\": \"Replace\"}")
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

  private def validateQuery(queryResponse: QueryResponse, docList: SolrDocumentList, isEmpty: Boolean) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).query(any[SolrQuery])
    there was one(queryResponse).getResults
    if (!isEmpty) {
      there was two(docList).get(0)
    }
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
