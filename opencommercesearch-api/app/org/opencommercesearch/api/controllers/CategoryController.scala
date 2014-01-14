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
import play.api.mvc.{SimpleResult, Action}
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

@Api(value = "categories", basePath = "/api-docs/categories", description = "Category API endpoints")
object CategoryController extends BaseController {

  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Searches categories", notes = "Returns category information for a given category", response = classOf[Category], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Category not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
      version: Int,
      @ApiParam(value = "A category id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
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

    withErrorHandling(future, s"Cannot retrieve category with id [$id]")
  }

  @ApiOperation(value = "Creates categories", notes = "Creates/updates the given categories", httpMethod = "PUT")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "categories", value = "Categories to create/update", required = true, dataType = "org.opencommercesearch.api.models.CategoryList", paramType = "body")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 400, message = "Missing required fields"),
    new ApiResponse(code = 400, message = "Exceeded maximum number of categories that can be created at once")
  ))
  def bulkCreateOrUpdate(
    version: Int,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create categories in preview", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async (parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[CategoryList](request.body).map { categoryList =>
      val categories = categoryList.categories
      if (categories.size > MaxUpdateCategoryBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of categories. Maximum is $MaxUpdateCategoryBatchSize")))
      } else {
        try {
          val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
          val docs = categoryList.toDocuments
          update.add(docs)

          val future: Future[SimpleResult] = update.process(solrServer).map( response => {
            Created
          })

          withErrorHandling(future, s"Cannot store categories with ids [${categories map (_.id) mkString ","}]")
      } catch {
          case e: IllegalArgumentException => {
            Logger.error(e.getMessage)
            Future.successful(BadRequest(Json.obj(
              "message" -> e.getMessage
            )))
          }
        }
      }
    }.recover {
      case e: JsError => {
        Future.successful(BadRequest(Json.obj(
          "message" -> "Missing required fields",
          "detail" -> JsError.toFlatJson(e))))
      }
    }.get
  }

  @ApiOperation(value = "Deletes categories", notes = "Deletes categories that were not updated in a given feed", httpMethod = "DELETE")
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All categories with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete categories in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-feedTimestamp:" + feedTimestamp)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })

    withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
  }

  @ApiOperation(value = "Suggests categories", notes = "Returns category suggestions for given partial category name", response = classOf[Category], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial category name is too short")))
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
      preview: Boolean) = Action.async { implicit request =>
    val solrQuery = withCategoryCollection(new SolrQuery(query), preview)
    //@todo: solrQuery.addFilterQuery(s"catalogs:$site")

    findSuggestionsFor(classOf[Category], "categories" , solrQuery)
  }
}
