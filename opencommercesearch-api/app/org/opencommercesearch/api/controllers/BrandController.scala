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
import play.api.libs.json.{JsError, JsObject, Json}
import scala.concurrent.Future
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.util.Util
import Util._
import org.opencommercesearch.api.models.{Category, Brand, BrandList}
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import com.wordnik.swagger.annotations._
import javax.ws.rs.{QueryParam, PathParam}
import play.api.mvc.SimpleResult
import org.apache.solr.client.solrj.response.UpdateResponse
import org.opencommercesearch.api.service.CategoryService
import scala.collection.JavaConversions._
import org.opencommercesearch.api.common.FacetQuery

@Api(value = "brands", basePath = "/api-docs/brands", description = "Brand API endpoints")
object BrandController extends BaseController with FacetQuery {

  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Get a brand by id", notes = "Returns brand information for a given brand", response = classOf[Brand], httpMethod = "GET")
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
    Logger.debug("Query brand " + id)
    val storage = withNamespace(storageFactory, preview)
    val storageFuture = storage.findBrand(id, fieldList(allowStar = true)).map( brand => {
      if (brand != null) {
        Logger.debug("Found brand " + id)
        Ok(Json.obj(
          "brand" -> Json.toJson(brand)))
      } else {
        Logger.debug("Brand " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find brand with id [$id]"
        ))
      }
    })

    withErrorHandling(storageFuture , s"Cannot retrieve brand with id [$id]")
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
    preview: Boolean) = Action.async (parse.json) { implicit request =>
    Json.fromJson[Brand](request.body).map { brand =>
      if (brand.name.isEmpty) {
        Logger.error("Missing required brand fields [name]")
        Future.successful(BadRequest(Json.obj("message" -> "Missing required fields")))
      } else {
        try {
          //Save brand on storage
          val storage = withNamespace(storageFactory, preview)
          val storageFuture = storage.saveBrand(brand)

          val update = withBrandCollection(new AsyncUpdateRequest(), preview)
          update.add(brand.toDocument(System.currentTimeMillis()))

          val searchFuture = update.process(solrServer)
          val future: Future[SimpleResult] = storageFuture zip searchFuture map { case (r1, r2) =>
            Created.withHeaders((LOCATION, absoluteURL(routes.BrandController.findById(id), request)))
          }

          withErrorHandling(future, s"Cannot store brand with id [$id]")
        } catch {
          case e: IllegalArgumentException =>
            Logger.error(e.getMessage)
            Future.successful(BadRequest(Json.obj(
              "message" -> e.getMessage)))
        }
      }
    }.recover {
      case e =>
        Logger.error(s"Missing required brand fields ${JsError.toFlatJson(e).toString()}")
        Future.successful(BadRequest(Json.obj(
          "message" -> "Missing required brand fields")))
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
        Logger.error("Missing required brand fields [id] or [name]")
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
      case e =>
        Logger.error(s"Missing required fields ${JsError.toFlatJson(e).toString()}")
        Future.successful(
          BadRequest(Json.obj(
            "message" -> "Missing required fields")))
    }.get
  }

  @ApiOperation(value = "Suggests brands", notes = "Returns brand suggestions for given partial brand name", response = classOf[Brand], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial brand name is too short")))
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
        brand.name.isEmpty 
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
   @ApiParam(value = "Site to search for brands", required = true)
   @QueryParam("site")
   site: String) = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    val catalogQuery = withSearchCollection(withFieldFacet("brandId", withFacetPagination(new SolrQuery("*:*"))), preview)
    catalogQuery.addFilterQuery("categoryPath:" + site)

    if(Logger.isDebugEnabled) {
      Logger.debug("Searching brand ids with query " + catalogQuery.toString)
    }

    val future = solrServer.query(catalogQuery).flatMap( catalogResponse => {
      val facetFields = catalogResponse.getFacetFields
      var brandInfoFuture : Future[SimpleResult] = null

      if(facetFields != null) {
        facetFields.map( facetField => {
          if("brandid".equals(facetField.getName.toLowerCase)) {
            if(Logger.isDebugEnabled) {
              Logger.debug("Got " + facetField.getValueCount + " brand ids")
            }

            if(facetField.getValueCount > 0) {
              //Find brand data on storage
              val storage = withNamespace(storageFactory, preview)
              val brandIds = facetField.getValues.map(facetValue => {facetValue.getName})

              brandInfoFuture = storage.findBrands(brandIds, fieldList(allowStar = true)).map( brandList => {
                Ok(Json.obj(
                  "metadata" -> Json.obj(
                    "time" -> (System.currentTimeMillis() - startTime)),
                  "brands" -> Json.toJson(brandList))
                )
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
        Future(NotFound(Json.obj("message" -> "No brands found")))
      }
    })

    withErrorHandling(future, "Cannot get brands")
  }

  /**
   * Get a list of all categories for a given brand
   */
  @ApiOperation(value = "Get all categories for a given brand", notes = "Gets a list of the categories associated with a given brand. Only categories that have products in stock are returned. Returns all the corresponding taxonomy for each category found.", response = classOf[Category], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited category field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findBrandCategoriesById(
   version: Int,
   @ApiParam(value = "Brand id to get categories for", required = true)
   @PathParam("id")
   id: String,
   @ApiParam(defaultValue="false", allowableValues="true,false", value = "Get categories for brand in preview", required = false)
   @QueryParam("preview")
   preview: Boolean,
   @ApiParam(value = "Site to search categories for brand", required = true)
   @QueryParam("site")
   site: String) = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    val catalogQuery = withSearchCollection(withFieldFacet("ancestorCategoryId", new SolrQuery("*:*")), preview)

    catalogQuery.setFacetLimit(MaxFacetPaginationLimit)
    catalogQuery.addFilterQuery("categoryPath:" + site)
    catalogQuery.addFilterQuery("brandId:" + id)

    if(Logger.isDebugEnabled) {
      Logger.debug(s"Searching categories for brand $id with query ${catalogQuery.toString}")
    }

    val future = solrServer.query(catalogQuery).flatMap( catalogResponse => {
      val facetFields = catalogResponse.getFacetFields
      var taxonomyFuture: Future[SimpleResult] = null

      if(facetFields != null) {
        facetFields.map( facetField => {
          if("ancestorcategoryid".equals(facetField.getName.toLowerCase)) {
            if(Logger.isDebugEnabled) {
              Logger.debug(s"Got ${facetField.getValueCount} category ids for brand $id")
            }

            val storage = withNamespace(storageFactory, preview)

            if(facetField.getValueCount > 0) {
              val categoryIds = facetField.getValues.map(facetValue => {facetValue.getName})
              Logger.debug(s"Category ids for brand $id are $categoryIds")

              val categoryFuture = categoryService.getTaxonomy(categoryIds, fieldList(allowStar = true), storage)

              taxonomyFuture = categoryFuture.flatMap(categoryTaxonomy => {
                Future(Ok(Json.obj(
                  "metadata" -> Json.obj(
                    "time" -> (System.currentTimeMillis() - startTime)),
                  "categories" -> Json.toJson(facetField.getValues.map( facetValue => {categoryTaxonomy(facetValue.getName)})))
                ))
              })
            }
          }
        })
      }
      else {
        Logger.debug("Got 0 categories, no facets were returned")
      }

      if(taxonomyFuture != null) {
        withErrorHandling(taxonomyFuture, s"Found categories for brand $id, but could not resolve taxonomy")
      }
      else {
        Future(NotFound(Json.obj("message" -> s"No categories found for brand $id")))
      }
    })

    withErrorHandling(future, s"Cannot get categories for brand $id")
  }

  /**
   * Get a list of all brand categories
   */
  @ApiOperation(value = "Get all brand categories", notes = "Gets a list of brand categories that have products in stock.", response = classOf[Category], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the brands list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of brands", defaultValue = "10", required = false, dataType = "int", paramType = "query")
  ))
  def findBrandCategories(
   version: Int,
   @ApiParam(defaultValue="false", allowableValues="true,false", value = "Get brand categories in preview", required = false)
   @QueryParam("preview")
   preview: Boolean,
   @ApiParam(value = "Site to search for brand categories", required = true)
   @QueryParam("site")
   site: String) = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    val brandsQuery = withSearchCollection(withFacetPagination(withFieldFacet("brandId", new SolrQuery("*:*"))), preview)

    //Filter by site
    brandsQuery.addFilterQuery("categoryPath:" + site)

    Logger.debug(s"Searching brands ${brandsQuery.toString}")

    val future = solrServer.query(brandsQuery).flatMap( brandsResponse => {
      val facetFields = brandsResponse.getFacetFields
      var brandsDataFuture: Future[SimpleResult] = null

      if(facetFields != null) {
        facetFields.map( brandsFacetField => {
          if("brandid".equals(brandsFacetField.getName.toLowerCase)) {
            if(Logger.isDebugEnabled) {
              Logger.debug(s"Got ${brandsFacetField.getValueCount} brand ids")
            }

            if(brandsFacetField.getValueCount > 0) {
              //Need to get brands data
              val storage = withNamespace(storageFactory, preview)
              val brandIds = brandsFacetField.getValues.map(facetValue => {facetValue.getName})

              val brandsFromStorage = storage.findBrands(brandIds, Seq("*"))

              brandsDataFuture = brandsFromStorage.flatMap(brandList => {
                val resultList = brandList.map( brand => {
                  val categoryQuery = withSearchCollection(withFieldFacet("ancestorCategoryId", new SolrQuery("*:*")), preview)
                  categoryQuery.setFacetLimit(MaxFacetPaginationLimit)
                  //Filter by site
                  categoryQuery.addFilterQuery(s"categoryPath:" + site)
                  categoryQuery.addFilterQuery("brandId:" + brand.getId)

                  val categoriesQuery = solrServer.query(categoryQuery).flatMap( categoryResponse => {
                    val facetFields = categoryResponse.getFacetFields
                    var categoryDataFuture: Future[Iterable[JsObject]] = null

                    if(facetFields != null) {
                      facetFields.map(categoriesFacetField => {
                        if("ancestorcategoryid".equals(categoriesFacetField.getName.toLowerCase)) {
                          if(Logger.isDebugEnabled) {
                            Logger.debug(s"Got ${categoriesFacetField.getValueCount} category ids for brand ${brand.getId}")
                          }

                          if(categoriesFacetField.getValueCount > 0) {
                            val categoryIds = categoriesFacetField.getValues.map(facetValue => {facetValue.getName})

                            Logger.debug(s"Category ids for brand ${brand.getId} are $categoryIds")

                            categoryDataFuture = storage.findCategories(categoryIds, Seq("*")).map( categoryList => {
                              categoryList filter { category =>
                                category.catalogs.getOrElse(Seq.empty[String]).contains(site) &&
                                !category.isRuleBased.getOrElse(false) &&
                                 category.parentCategories.getOrElse(Seq.empty[Category]).size != 0
                              } map( category => {
                                Json.obj(
                                  "brandId" -> brand.getId,
                                  "brandName" -> brand.getName,
                                  "categoryId" -> category.getId,
                                  "categoryName" -> category.getName,
                                  "name" -> s"${brand.getName} ${category.getName}",
                                  "seoUrlToken" -> s"${brand.getSeoUrlToken}-${category.getSeoUrlToken}"
                                )
                              })
                            })
                          }
                          else {
                            Logger.debug("Got an empty ancestorCategoryId facet field for brand ${brand.getId}")
                          }
                        }
                        else {
                          Logger.debug(s"Cannot get categories for brand ${brand.getId} because there is no ancestorCategoryId facet field in Solr response")
                        }
                      })
                    }
                    else {
                      Logger.debug(s"Cannot get categories for brand ${brand.getId} because there are no facet fields in Solr response")
                    }

                    if(categoryDataFuture != null) {
                      categoryDataFuture
                    }
                    else {
                      Future(Iterable.empty[JsObject])
                    }
                  })

                  categoriesQuery
                })

                //Combine all futures in a single one (wait until they all finish)
                Future.sequence(resultList).map(brandList => {
                  Ok(Json.obj(
                    "metadata" -> Json.obj(
                      "time" -> (System.currentTimeMillis() - startTime)),
                    "brandCategories" -> brandList.foldRight(List.empty[Iterable[JsObject]]) {
                    (iterable, accum) =>
                      iterable :: accum
                  }))
                })
              })
            }
            else {
              Logger.debug("Got a an empty facet field for brandId")
            }
          }
          else {
            Logger.debug("Cannot get brand categories because there is no brandId facet field in Solr response")
          }
        })
      }
      else {
        Logger.debug("Cannot get brand categories because there are no facet fields in Solr response")
      }

      if(brandsDataFuture != null) {
        withErrorHandling(brandsDataFuture, s"Cannot get brand categories")
      }
      else {
        Future(NotFound(Json.obj("message" -> s"No brands found")))
      }
    })

    withErrorHandling(future, s"Cannot get brand categories")
  }
}
