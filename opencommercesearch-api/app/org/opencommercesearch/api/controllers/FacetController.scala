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
import play.api.Logger
import play.api.libs.json.{JsError, JsArray, Json}

import scala.concurrent.Future

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.Util._
import org.opencommercesearch.api.models.{Facet, FacetList}
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.beans.BindingException
import org.opencommercesearch.api.common.{FieldList, ContentPreview}

object FacetController extends BaseController {

  def findById(version: Int, id: String, preview: Boolean) = Action.async { implicit request =>
    val query = withFacetCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview, request.acceptLanguages)

    query.add("q", "id:" + id)
    query.add("fl", "*")

    Logger.debug("Query facet " + id)
    val future = solrServer.query(query).map( response => {
      val doc = response.getResults.get(0)

      if (doc != null) {
        Logger.debug("Found facet " + id)
        Ok(Json.obj(
          "facet" -> solrServer.binder.getBean(classOf[Facet], doc)))
      } else {
        Logger.debug("Facet " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find facet with id [$id]"
        ))
      }
    })

    withErrorHandling(future, s"Cannot retrieve facet with id [$id]")
  }

  def createOrUpdate(version: Int, id: String, preview: Boolean) = Action.async (parse.json) { request =>
    Json.fromJson[Facet](request.body).map { facet =>
      try {
        val facetDoc = solrServer.binder.toSolrInputDocument(facet)
        val update = new AsyncUpdateRequest()
        update.add(facetDoc)
        withFacetCollection(update, preview, request.acceptLanguages)

        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.FacetController.findById(id), request)))
        })

        withErrorHandling(future, s"Cannot store Facet with id [$id]")
      }
      catch {
        case e : BindingException =>
          Future.successful(BadRequest(Json.obj(
            "message" -> "Illegal Facet fields")))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        "message" -> "Illegal Facet fields")))
    }.get
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action.async (parse.json) { request =>
    Json.fromJson[FacetList](request.body).map { facetList =>
      val facets = facetList.facets
      try {
        if (facets.length > MaxUpdateFacetBatchSize) {
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Exceeded number of Facets. Maximum is $MaxUpdateFacetBatchSize")))
        } else {
          val update = withFacetCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)
          facets map { facet =>
              update.add(solrServer.binder.toSolrInputDocument(facet))
          }

          val future: Future[SimpleResult] = update.process(solrServer).map( response => {
            Created(Json.obj(
              "locations" -> JsArray(
                facets map (b => Json.toJson(routes.FacetController.findById(b.id.get).url))
              )))
          })

          withErrorHandling(future, s"Cannot store Facets with ids [${facets map (_.id.get) mkString ","}]")
        }
      }
      catch {
        case e : BindingException =>
          //Handle bind exceptions
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Illegal Facet fields [${facets map (_.id.get) mkString ","}]")))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields",
        "detail" -> JsError.toFlatJson(e))))
    }.get
  }

  /**
   * Post method for the facets endpoint. Will send commit or rollback to Solr accordingly.
   * @param commit true if a commit should be done.
   * @param rollback true if a rollbac should be done.
   */
  def commitOrRollback(preview: Boolean, commit: Boolean, rollback: Boolean) = Action.async { request =>
    if(commit == rollback) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"commit and boolean can't have the same value.")))
    }
    else {
      val update = new AsyncUpdateRequest()
      withFacetCollection(update, preview, request.acceptLanguages)

      if(commit) {
        update.setAction(ACTION.COMMIT, false, false, false)
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "commit success"))
        })

        withErrorHandling(future, s"Cannot commit facets.")
      }
      else {
        update.rollback
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "rollback success"))
        })

        withErrorHandling(future, s"Cannot rollback facets.")
      }
    }
  }

  /**
   * Delete method that remove all facets matching a given query.
   * @param query is the query used to delete facets, default is *:*
   */
  def deleteByQuery(preview: Boolean, query: String) = Action.async { request =>
    val update = new AsyncUpdateRequest()
    withFacetCollection(update, preview, request.acceptLanguages)

    update.deleteByQuery(query)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      Ok (Json.obj(
        "message" -> "delete success"))
    })

    withErrorHandling(future, s"Cannot delete facets.")
  }
}
