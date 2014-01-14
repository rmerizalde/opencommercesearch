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
import play.api.libs.json.Json
import scala.concurrent.Future
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.util.Util
import Util._
import org.opencommercesearch.api.models.{Brand, BrandList}
import org.apache.solr.common.SolrDocument
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import com.wordnik.swagger.annotations._
import javax.ws.rs.{QueryParam, PathParam}
import scala.collection.convert.Wrappers.JIterableWrapper
import play.api.mvc.SimpleResult
import org.apache.solr.client.solrj.response.UpdateResponse

@Api(value = "brands", basePath = "/api-docs/brands", description = "Brand API endpoints")
object BrandController extends BaseController {

  @ApiOperation(value = "Searches brands", notes = "Returns brand information for a given brand", response = classOf[Brand], httpMethod = "GET")
  @ApiResponses(Array(new ApiResponse(code = 404, message = "Brand not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
    version: Int,
    @ApiParam(value = "A brand id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async { implicit request =>
    val query = withBrandCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview)

    query.setRequestHandler(RealTimeRequestHandler)
    query.add("id", id)
    //TODO: change this code to retrieve data from mongodb
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

    withErrorHandling(future, s"Cannot retrieve brand with id [$id]")
  }

  @ApiOperation(value = "Creates a brand", notes = "Creates/updates the given brand", httpMethod = "PUT")
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Missing required fields")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "brand", value = "Brand to create/update", required = true, dataType = "org.opencommercesearch.api.models.Brand", paramType = "body")
  ))
  def createOrUpdate(
      version: Int,
      @ApiParam( value = "A brand id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create the brand in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async (parse.json) { request =>
    Json.fromJson[Brand](request.body).map { brand =>
      if (brand.name.isEmpty || brand.logo.isEmpty) {
        Future.successful(BadRequest(Json.obj("message" -> "Missing required fields")))
      } else {
        //TODO: add code to update mongodb
        val brandDoc = brand.toDocument(System.currentTimeMillis())

        brandDoc.setField("id", id)

        val update = new AsyncUpdateRequest()
        update.add(brandDoc)
        withBrandCollection(update, preview)

        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.BrandController.findById(id), request)))
        })

        withErrorHandling(future, s"Cannot store brand with id [$id]")
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
          // @TODO figure out how to pull missing field from JsError
          "message" -> "Illegal brand fields")))
    }.get
  }

  @ApiOperation(value = "Creates brands", notes = "Creates/updates the given brands", httpMethod = "PUT")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "brands", value = "Brands to create/update", required = true, dataType = "org.opencommercesearch.api.models.BrandList", paramType = "body")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 400, message = "Missing required fields"),
    new ApiResponse(code = 400, message = "Exceeded maximum number of brands that can be created at once")
  ))
  def bulkCreateOrUpdate(
      version: Int,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Create brands in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async (parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[BrandList](request.body).map { brandList =>
      val brands = brandList.brands

      if (brands.length > MaxUpdateBrandBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of brands. Maximum is $MaxUpdateBrandBatchSize")))
      } else if (hasMissingFields(brands)) {
        Future.successful(BadRequest(Json.obj(
          "message" -> "Missing required fields")))
      } else {
        
        val storage = withNamespace(storageFactory, preview)
        val storageFuture = storage.saveBrand(brands:_*)
          
        val update = withBrandCollection(new AsyncUpdateRequest(), preview)
        update.add(brandList.toDocuments)

        val searchFuture: Future[UpdateResponse] = update.process(solrServer)
        
        val future: Future[SimpleResult] =  storageFuture zip searchFuture map { case (r1, r2) =>
            Created
        }

        withErrorHandling(future, s"Cannot store brands with ids [${brands map (_.id.get) mkString ","}]")
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields")))
    }.get
  }

  @ApiOperation(value = "Suggests brands", notes = "Returns brand suggestions for given partial brand name", response = classOf[Brand], httpMethod = "GET")
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
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean) = Action.async { implicit request =>
    val solrQuery = withBrandCollection(new SolrQuery(query), preview)

    findSuggestionsFor(classOf[Brand], "brands" , solrQuery)
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

  /**
   * Delete method that remove all brands not matching a given timestamp.
   */
  @ApiOperation(value = "Delete brands", notes = "Removes brands based on a given query", httpMethod = "DELETE")
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Cannot delete brands")))
  def deleteByTimestamp(
   @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete brands in preview", required = false)
   @QueryParam("preview")
   preview: Boolean,
   @ApiParam(value = "The feed timestamp. All brands with a different timestamp are deleted", required = true)
   @QueryParam("feedTimestamp")
   feedTimestamp: Long) = Action.async { request =>
    deleteByQuery("-feedTimestamp:" + feedTimestamp, withBrandCollection(new AsyncUpdateRequest(), preview), "brands")
  }

  /**
   * Get a list of all existing brands
   */
  @ApiOperation(value = "Get all brands", notes = "Gets a list of all brands", response = classOf[Brand], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the brands list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of brands", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findAll(
   version: Int,
   @ApiParam(defaultValue="false", allowableValues="true,false", value = "Get brands in preview", required = false)
   @QueryParam("preview")
   preview: Boolean,
   @ApiParam(allowableValues = "bcs,competitivecyclist,dogfunk", value = "Site to search for brands", required = true)
   @QueryParam("site")
   site: String) = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    val catalogQuery = withSearchCollection(withFacetParams(withFacetPagination(new SolrQuery("*:*")), "brandId"), preview)
    catalogQuery.addFilterQuery("categoryPath:" + site)
    catalogQuery.setRows(0) //No document results needed

    if(Logger.isDebugEnabled) {
      Logger.debug("Searching for brand ids with query " + catalogQuery.toString)
    }
    //TODO: change this code to retrieve data from mongodb
    val future = solrServer.query(catalogQuery).flatMap( catalogResponse => {
      val facetFields = catalogResponse.getFacetFields
      var brandInfoFuture : Future[SimpleResult] = null

      if(facetFields != null) {
        JIterableWrapper(facetFields).map( facetField => {
          if("brandid".equals(facetField.getName.toLowerCase())) {
            if(Logger.isDebugEnabled) {
              Logger.debug("Got " + facetField.getValueCount + " brand ids")
            }

            if(facetField.getValueCount > 0) {
              val ids = new StringBuilder

              JIterableWrapper(facetField.getValues).map( facetValue => {
                ids.append(facetValue.getName)
                ids.append(",")
              })

              ids.setLength(ids.length - 1)

              //Use real time get to get all fields from returned brands.
              val query = withBrandCollection(new SolrQuery(), preview)
              query.setRequestHandler(RealTimeRequestHandler)
              query.add("ids", ids.toString())
              withFields(query, request.getQueryString("fields"))

              if(Logger.isDebugEnabled) {
                Logger.debug("Getting fields from returned brands with query " + query.toString)
              }

              brandInfoFuture = solrServer.query(query).map( response => {

                if(Logger.isDebugEnabled) {
                  Logger.debug("Got " + response.getResults.getNumFound + " brand info results")
                }

                if(response.getResults.getNumFound > 0) {
                  Ok(Json.obj(
                    "time" -> (System.currentTimeMillis() - startTime),
                    "brands" -> Json.toJson(JIterableWrapper(solrServer.binder.getBeans(classOf[Brand], response.getResults))))
                  )
                }
                else {
                  InternalServerError(Json.obj("message" -> "No brand info available"))
                }
              })
            }
          }
        })
      }
      else {
          Logger.debug("Got 0 brands, no facets were returned")
      }

      if(brandInfoFuture != null) {
        withErrorHandling(brandInfoFuture, "Cannot get brands")
      }
      else {
        Future(InternalServerError(Json.obj("message" -> "No brands found")))
      }
    })

    withErrorHandling(future, "Cannot get brands")
  }

  /**
   * Helper method to set the fields to return for each product
   *
   * @param query is the Solr query
   * @param facetField is the field used for faceting
   * @return
   */
  private def withFacetParams(query: SolrQuery, facetField : String) : SolrQuery = {
    query.setFacet(true)
      .setFacetMinCount(1)
      .add("facet.field", facetField)

    query
  }
}
