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

import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.Future

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.apache.solr.client.solrj.response.{GroupCommand, GroupResponse, QueryResponse}
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.{SolrRequest, SolrQuery}

abstract class BaseSpec extends Specification with Mockito {
  protected def setupQuery = {
    val queryResponse = mock[QueryResponse]
    val namedList = mock[NamedList[AnyRef]]

    queryResponse.getResponse returns namedList
    solrServer.query(any[SolrQuery]) returns Future.successful(queryResponse)

    (queryResponse, namedList)
  }

  protected def validateQuery(queryResponse: QueryResponse, namedList: NamedList[AnyRef], expectedQueries: Int = 1) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).query(any[SolrQuery])
    there was one(queryResponse).getResponse
    there was one(namedList).get("doc")
  }

  protected def validateQueryResult(result: Result, expectedStatus: Int, expectedContentType: String, expectedContent: String = null) = {
    status(result) must equalTo(expectedStatus)
    contentType(result).get must beEqualTo(expectedContentType)
    if (expectedContent != null) {
      contentAsString(result) must contain (expectedContent)
    }
  }

  protected def validateUpdate(updateResponse: NamedList[AnyRef]) = {
    // @todo figure out how to wait for async code executed through the route code
    Thread.sleep(300)
    there was one(solrServer).request(any[SolrRequest])
  }

  protected def validateFailedUpdate(updateResponse: NamedList[AnyRef]) = {
    there was no(solrServer).request(any[SolrRequest])
  }

  protected def validateUpdateResult(result: Result, expectedStatus: Int, expectedLocation: String = null) = {
    status(result) must equalTo(expectedStatus)
    if (expectedLocation != null) {
      headers(result).get(LOCATION).get must endWith(expectedLocation)
    }
  }

  protected def validateFailedUpdateResult(result: Result, expectedStatus: Int, expectedContent: String) = {
    status(result) must equalTo(expectedStatus)
    contentAsString(result) must contain (expectedContent)
  }

  protected def setupUpdate = {
    val updateResponse = mock[NamedList[AnyRef]]

    solrServer.request(any[SolrRequest]) returns Future.successful(updateResponse)
    (updateResponse)
  }
}
