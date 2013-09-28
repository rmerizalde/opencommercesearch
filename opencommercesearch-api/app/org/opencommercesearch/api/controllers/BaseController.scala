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


import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.libs.json.{Writes, Json}

import scala.concurrent.Future
import scala.collection.convert.Wrappers.JIterableWrapper

import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.common.{FieldList, ContentPreview}

import org.opencommercesearch.api.Global._

/**
 * This class provides common functionality for all controllers
 *
 * @todo refactor other functionality to this controller
 *
 * @author rmerizalde
 */
class BaseController extends Controller with ContentPreview with FieldList with Pagination with ErrorHandling {

  protected def findSuggestionsFor[T](clazz: Class[T], typeName: String, query: SolrQuery)(implicit req: Request[AnyContent], c: Writes[T]) : Future[Result] = {
    val startTime = System.currentTimeMillis()
    val solrQuery = withPagination(withFields(query, req.getQueryString("fields")))

    if (query == null || query.getQuery.length < 2) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"At least $MinSuggestQuerySize characters are needed to make suggestions"
      )))
    } else {
      val future = solrServer.query(solrQuery).map( response => {
        val docs = response.getResults
        Ok(Json.obj(
          "metadata" -> Json.obj(
             "found" -> docs.getNumFound,
             "time" -> (System.currentTimeMillis() - startTime)),
          "suggestions" -> JIterableWrapper(docs).map(doc => solrServer.binder.getBean(clazz, doc))
        ))
      })

      withErrorHandling(future, s"Cannot suggest $typeName  for [${query.getQuery}]")
    }
  }
}
