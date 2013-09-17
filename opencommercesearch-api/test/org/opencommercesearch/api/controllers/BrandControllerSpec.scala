package org.opencommercesearch.api.controllers

import play.api.mvc.Result
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.{JsError, Json, JsArray}

import scala.concurrent.Future

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.apache.solr.client.solrj.AsyncSolrServer
import org.apache.solr.client.solrj.{SolrRequest, SolrQuery}
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.util.NamedList
import org.apache.solr.common.SolrDocument
import org.opencommercesearch.api.models.Brand

import org.opencommercesearch.api.Global._


class BrandControllerSpec extends Specification with Mockito {

  trait Brands extends Before {
    def before = {
      solrServer = mock[AsyncSolrServer]
    }
  }

  sequential

  "Brand Controller" should {

    "send 404 on an unknown route" in new Brands {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "send 404 on a get to all brands"  in new Brands {
      running(FakeApplication()) {

        route(FakeRequest(GET, "/v1/brands")) must beNone
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

    // @todo test bulk update
    "send 400 when exceeding maximum brands an a bulk create" in new Brands {
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

        val jsonResponse = Json.parse(contentAsString(result.get))
        val jsLocations = (jsonResponse \\ "locations").asInstanceOf[List[JsArray]]

        val expectedUrls = Set(routes.BrandController.findById(expectedId).url, routes.BrandController.findById(expectedId2).url)

        for (jsLocation <- jsLocations) {
          for (location <- jsLocation.value) {
            expectedUrls should contain (location.as[String])
          }
        }
      }
    }
  }

  private def setupQuery = {
    val queryResponse = mock[QueryResponse]
    val namedList = mock[NamedList[AnyRef]]

    queryResponse.getResponse returns namedList
    solrServer.query(any[SolrQuery]) returns Future.successful(queryResponse)
    (queryResponse, namedList)
  }

  private def validateQuery(queryResponse: QueryResponse, namedList: NamedList[AnyRef]) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).query(any[SolrQuery])
    there was one(queryResponse).getResponse
    there was one(namedList).get("doc")
  }

  private def validateQueryResult(result: Result, expectedStatus: Int, expectedContentType: String, expectedContent: String = null) = {
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

  private def validateUpdateResult(result: Result, expectedStatus: Int, expectedLocation: String = null) = {
    status(result) must equalTo(expectedStatus)
    if (expectedLocation != null) {
      headers(result).get(LOCATION).get must endWith(expectedLocation)
    }
  }

  private def validateFailedUpdateResult(result: Result, expectedStatus: Int, expectedContent: String) = {
    status(result) must equalTo(expectedStatus)
    contentAsString(result) must contain (expectedContent)
  }

  private def setupUpdate = {
    val updateResponse = mock[NamedList[AnyRef]]

    solrServer.request(any[SolrRequest]) returns Future.successful(updateResponse)
    (updateResponse)
  }
}
