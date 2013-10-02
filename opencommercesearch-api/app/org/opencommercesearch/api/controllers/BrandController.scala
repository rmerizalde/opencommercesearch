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
import play.api.libs.json.{JsArray, Json}

import scala.concurrent.Future

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.Util._
import org.opencommercesearch.api.models.{Brand, BrandList}
import org.apache.solr.common.SolrDocument
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import com.wordnik.swagger.annotations._
import javax.ws.rs.{QueryParam, PathParam}

@Api(value = "/brands", listingPath = "/api-docs/brands", description = "Brand API endpoints")
object BrandController extends BaseController {

  @ApiOperation(value = "Searches brands", notes = "Returns brand information for a given brand", responseClass = "org.opencommercesearch.api.models.Brand", httpMethod = "GET")
  @ApiErrors(value = Array(new ApiError(code = 404, reason = "Brand not found")))
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
    version: Int,
    @ApiParam(value = "A brand id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action { implicit request =>
    val query = withBrandCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview)

    query.setRequestHandler(RealTimeRequestHandler)
    query.add("id", id)

    Logger.debug("Query brand " + id)
    val future = solrServer.query(query).map( response => {
      val doc = response.getResponse.get("doc").asInstanceOf[SolrDocument]
      if (doc != null) {
        Logger.debug("Found brand " + id)
        Ok(Json.obj(
          "brand" -> Json.toJson(Brand.fromDocument(doc))))
      } else {
        Logger.debug("Brand " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find brand with id [$id]"
        ))
      }
    })

    Async {
      withErrorHandling(future, s"Cannot retrieve brand with id [$id]")
    }
  }

  @ApiOperation(value = "Creates a brand", notes = "Creates/updates the given brand", httpMethod = "PUT")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "brand", value = "Brand to create/update", required = true, dataType = "org.opencommercesearch.api.models.Brand", paramType = "body")
  ))
  @ApiErrors(value = Array(
    new ApiError(code = 400, reason = "Missing required fields")
  ))
  def createOrUpdate(
      version: Int,
      @ApiParam( value = "A brand id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create the brand in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action (parse.json) { request =>
    Json.fromJson[Brand](request.body).map { brand =>
      if (brand.name.isEmpty || brand.logo.isEmpty) {
        BadRequest(Json.obj("message" -> "Missing required fields"))
      } else {
        val brandDoc = brand.toDocument

        brandDoc.setField("id", id)

        val update = new AsyncUpdateRequest()
        update.add(brandDoc)
        withBrandCollection(update, preview)

        val future: Future[Result] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.BrandController.findById(id), request)))
        })

        Async {
          withErrorHandling(future, s"Cannot store brand with id [$id]")
        }
      }
    }.recoverTotal {
      e => BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Illegal brand fields"))
    }
  }

  @ApiOperation(value = "Creates brands", notes = "Creates/updates the given brands", httpMethod = "PUT")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "brands", value = "Brands to create/update", required = true, dataType = "org.opencommercesearch.api.models.BrandList", paramType = "body")
  ))
  @ApiErrors(value = Array(
    new ApiError(code = 400, reason = "Missing required fields"),
    new ApiError(code = 400, reason = "Exceeded maximum number of brands that can be created at once")
  ))
  def bulkCreateOrUpdate(
      version: Int,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create brands in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action(parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[BrandList](request.body).map { brandList =>
      val brands = brandList.brands

      if (brands.length > MaxUpdateBrandBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of brands. Maximum is $MaxUpdateBrandBatchSize"))
      } else if (hasMissingFields(brands)) {
        BadRequest(Json.obj(
          "message" -> "Missing required fields"))
      } else {
        val update = withBrandCollection(new AsyncUpdateRequest(), preview)
        update.add(brandList.toDocuments)

        val future: Future[Result] = update.process(solrServer).map( response => {
          Created
        })

        Async {
          withErrorHandling(future, s"Cannot store brands with ids [${brands map (_.id.get) mkString ","}]")
        }
      }
    }.recoverTotal {
      e => BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields"))
    }
  }

  @ApiOperation(value = "Suggests brands", notes = "Returns brand suggestions for given partial brand name", responseClass = "org.opencommercesearch.api.models.Brand", httpMethod = "GET")
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
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action { implicit request =>
    val solrQuery = withBrandCollection(new SolrQuery(query), preview)

    Async {
      findSuggestionsFor(classOf[Brand], "brands" , solrQuery)
    }
  }

  /**
   * Helper method to check if any of the brand is missing a field.
   * @param brands is the list of brands to be validated
   * @return true of any of the brands is missing a single field
   */
  private def hasMissingFields(brands: List[Brand]) : Boolean = {
    var missingFields = false
    val brandIt = brands.iterator
    while (!missingFields && brandIt.hasNext) {
      val brand = brandIt.next()
      missingFields = brand.id.isEmpty ||
        brand.name.isEmpty ||
        brand.logo.isEmpty
    }
    missingFields
  }
}
