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
import play.api.libs.json.{JsError, Json}

import scala.concurrent.Future

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.util.Util
import Util._
import org.opencommercesearch.api.models.{Facet, FacetList}
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.beans.BindingException
import com.wordnik.swagger.annotations._
import scala.Array
import play.api.libs.json.JsArray
import play.api.mvc.SimpleResult
import javax.ws.rs.{QueryParam, PathParam}
import org.apache.solr.client.solrj.response.UpdateResponse

@Api(value = "facets", basePath = "/api-docs/facets", description = "Facet API endpoints.")
object FacetController extends BaseController {

  @ApiOperation(value = "Searches facets", notes = "Returns information for a given facet", response = classOf[Facet], httpMethod = "GET")
  @ApiResponses(Array(new ApiResponse(code = 404, message = "Facet not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
    version: Int,
    @ApiParam(value = "A facet id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async { implicit request =>

    val query = withFacetCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview, request.acceptLanguages)

    query.add("q", "id:" + id)
    query.add("fl", "*")

    Logger.debug("Query facet " + id)
    val future = solrServer.query(query).flatMap( response => {
      val results = response.getResults
      Logger.debug("Num found " + results.getNumFound)
      if(results.getNumFound > 0 && results.get(0) != null) {
        val doc = results.get(0)
        Logger.debug("Found facet " + id)
        val facet = solrServer.binder.getBean(classOf[Facet], doc)
        val storage = withNamespace(storageFactory, preview)
        val storageFuture = storage.findFacet(id, Seq.empty)

        storageFuture map { facetFromStorage =>
          if(facetFromStorage != null) {
            Logger.debug("Found blacklist for facet " + id)
            facet.setBlackList(facetFromStorage.blackList.getOrElse(Seq.empty[String]))
          }

          Ok(Json.obj(
            "facet" -> facet))
        }
      }
      else {
        Logger.debug("Facet " + id + " not found")
        Future(NotFound(Json.obj(
          "message" -> s"Cannot find facet with id [$id]"
        )))
      }
    })

    withErrorHandling(future, s"Cannot retrieve facet with id [$id]")
  }

  @ApiOperation(value = "Creates a facet", notes = "Creates/updates the given facet", httpMethod = "PUT")
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Missing required fields")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "facet", value = "Facet to create/update", required = true, dataType = "org.opencommercesearch.api.models.Facet", paramType = "body")
  ))
  def createOrUpdate(
    version: Int,
    @ApiParam( value = "A facet id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create facet in preview", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async (parse.json) { implicit request =>
    Json.fromJson[Facet](request.body).map { facet =>
      try {
        val facetDoc = solrServer.binder.toSolrInputDocument(facet)
        val update = new AsyncUpdateRequest()
        update.add(facetDoc)
        withFacetCollection(update, preview, request.acceptLanguages)

        val storage = withNamespace(storageFactory, preview)
        val storageFuture = storage.saveFacet(facet)
        val searchFuture: Future[UpdateResponse] = update.process(solrServer)

        val future: Future[SimpleResult] = storageFuture zip searchFuture map { case (storageResult, searchResponse) =>
          Created.withHeaders((LOCATION, absoluteURL(routes.FacetController.findById(id), request)))
        }

        withErrorHandling(future, s"Cannot store facet with id [$id]")
      }
      catch {
        case e : BindingException =>
          Logger.error("Illegal facet fields", e)
          Future.successful(BadRequest(Json.obj(
            "message" -> "Illegal Facet fields")))
      }
    }.recover {
      case e =>
        Logger.error(s"Missing required fields ${JsError.toFlatJson(e)}")
        Future.successful(BadRequest(Json.obj(
        "message" -> "Missing required fields")))
    }.get
  }

  @ApiOperation(value = "Creates facets", notes = "Creates/updates the given facets", httpMethod = "PUT")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "facets", value = "Facets to create/update", required = true, dataType = "org.opencommercesearch.api.models.FacetList", paramType = "body")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 400, message = "Missing required fields"),
    new ApiResponse(code = 400, message = "Exceeded maximum number of facets that can be created at once")
  ))
  def bulkCreateOrUpdate(
    version: Int,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create facets in preview", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async (parse.json) { implicit request =>
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

          val storage = withNamespace(storageFactory, preview)
          val storageFuture = storage.saveFacet(facets:_*)
          val searchFuture: Future[UpdateResponse] = update.process(solrServer)

          val future = storageFuture zip searchFuture map { case (storageResponse, response) =>
            Created(Json.obj(
              "locations" -> JsArray(
                facets map (b => Json.toJson(routes.FacetController.findById(b.id.get).url))
              )))
          }

          withErrorHandling(future, s"Cannot store Facets with ids [${facets map (_.id.get) mkString ","}]")
        }
      }
      catch {
        case e : BindingException =>
          //Handle bind exceptions
          Logger.error("Illegal facet fields", e)
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Illegal Facet fields [${facets map (_.id.get) mkString ","}]")))
      }
    }.recover {
      case e =>
        Logger.error(s"Missing required fields ${JsError.toFlatJson(e)}")
        Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields")))
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
