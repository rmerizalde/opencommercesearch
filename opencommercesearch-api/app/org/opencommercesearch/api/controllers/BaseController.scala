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
import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.common.{FieldList, ContentPreview}
import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import scala.collection.convert.Wrappers.JIterableWrapper
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.opencommercesearch.api.common.FacetQuery

/**
 * This class provides common functionality for all controllers
 *
 * @todo refactor other functionality to this controller
 *
 * @author rmerizalde
 */
class BaseController extends Controller with ContentPreview with FieldList with FacetQuery with Pagination with ErrorHandling {

  protected def findSuggestionsFor[T](clazz: Class[T], typeName: String, query: SolrQuery)(implicit req: Request[AnyContent], c: Writes[T]) : Future[SimpleResult] = {
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

  /**
   * Delete method that removes all docs matching a given query.
   * @param query is the query used to delete docs, default is *:*
   * @param update is the update request used to delete docs. Child classes should set the collection the request should use.
   * @param typeName name of the item type to delete
   */
  protected def deleteByQuery(query: String, update: AsyncUpdateRequest, typeName: String) : Future[SimpleResult] = {
    update.deleteByQuery(query)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })

    withErrorHandling(future, s"Cannot delete $typeName")
  }

  /**
   * Will send commit or rollback to Solr accordingly.
   * @param commit true if a commit should be done.
   * @param rollback true if a rollback should be done.
   * @param update is the update request used to commit or rollback docs. Child classes should set the collection the request should use.
   * @param typeName name of the item type to commit or rollback
   */
  def commitOrRollback(commit: Boolean, rollback: Boolean, update: AsyncUpdateRequest, typeName: String) : Future[SimpleResult] = {
    if(commit == rollback) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"commit and boolean can't have the same value.")))
    }
    else {
      if(commit) {
        update.setAction(ACTION.COMMIT, false, false, false)
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "commit success"))
        })

        withErrorHandling(future, s"Cannot commit $typeName")
      }
      else {
        update.rollback
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "rollback success"))
        })

        withErrorHandling(future, s"Cannot rollback $typeName")
      }
    }
  }
}
