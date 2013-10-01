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
import play.api.mvc.{Result, Action}
import play.api.libs.json.{JsError, Json}
import play.api.Logger

import scala.concurrent.Future

import org.opencommercesearch.api.models.{Category, CategoryList}
import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.service.CategoryService
import com.wordnik.swagger.annotations._
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam

@Api(value = "/categories", listingPath = "/api-docs/categories", description = "Category API endpoints")
object CategoryController extends BaseController {

  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Searches categories", notes = "Returns category information for a given category", responseClass = "org.opencommercesearch.api.models.Category", httpMethod = "GET")
  @ApiErrors(value = Array(new ApiError(code = 404, reason = "Category not found")))
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
      version: Int,
      @ApiParam(value = "A category id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val future = categoryService.findById(id, preview, request.getQueryString("fields")).map(category => {
      if (category.isDefined) {
        Ok(Json.obj(
          "category" -> Json.toJson(category.get)))
      } else {
        Logger.debug("Category " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find category with id [$id]"
        ))
      }
    })

    Async {
      withErrorHandling(future, s"Cannot retrieve category with id [$id]")
    }
  }

  @ApiOperation(value = "Creates categories", notes = "Creates/updates the given categories", httpMethod = "PUT")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "categories", value = "Categories to create/update", required = true, dataType = "org.opencommercesearch.api.models.CategoryList", paramType = "body")
  ))
  @ApiErrors(value = Array(
    new ApiError(code = 400, reason = "Missing required fields"),
    new ApiError(code = 400, reason = "Exceeded maximum number of categories that can be created at once")
  ))
  def bulkCreateOrUpdate(
    version: Int,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create categories in preview", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action(parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[CategoryList](request.body).map { categoryList =>
      val categories = categoryList.categories
      if (categories.size > MaxUpdateCategoryBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of categories. Maximum is $MaxUpdateCategoryBatchSize"))
      } else {
        try {
          val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
          val docs = categoryList.toDocuments()
          update.add(docs)

          val future: Future[Result] = update.process(solrServer).map( response => {
            Created
          })

          Async {
            withErrorHandling(future, s"Cannot store products with ids [${categories map (_.id) mkString ","}]")
          }
        } catch {
          case e: IllegalArgumentException => {
            Logger.error(e.getMessage)
            BadRequest(Json.obj(
              "message" -> e.getMessage
            ))
          }
        }
      }
    }.recoverTotal {
      case e: JsError => {
        BadRequest(Json.obj(
          "message" -> "Missing required fields"))
      }
    }
  }

  @ApiOperation(value = "Deletes categories", notes = "Deletes categories that were not updated in a given feed", httpMethod = "DELETE")
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All categories with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete categories in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-feedTimestamp:" + feedTimestamp)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
    }
  }

  @ApiOperation(value = "Suggests categories", notes = "Returns category suggestions for given partial category name", responseClass = "org.opencommercesearch.api.models.Category", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  @ApiErrors(value = Array(new ApiError(code = 400, reason = "Partial category name is too short")))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial category name", required = true)
      @QueryParam("q")
      query: String,
      @ApiParam(value = "Site to search", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val solrQuery = withCategoryCollection(new SolrQuery(query), preview)
    //@todo: solrQuery.addFilterQuery(s"catalogs:$site")
    Async {
      findSuggestionsFor(classOf[Category], "categories" , solrQuery)
    }
  }
}
