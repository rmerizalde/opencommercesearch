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
import play.api.libs.json.{JsError, Json}
import play.api.Logger
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.collection.convert.Wrappers.JIterableWrapper
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.ProductFacetQuery
import org.opencommercesearch.api.common.FacetQuery
import org.opencommercesearch.api.models.{Category, Brand, CategoryList}
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.search.suggester.IndexableElement
import com.wordnik.swagger.annotations._
import org.opencommercesearch.api.common.FilterQuery
import org.apache.commons.lang3.StringUtils
import java.net.URLDecoder

@Api(value = "categories", basePath = "/api-docs/categories", description = "Category API endpoints")
object CategoryController extends BaseController with FacetQuery {

  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Searches categories", notes = "Returns category information for a given category", response = classOf[Category], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Category not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "maxLevels", value = "Max taxonomy levels to return. For example, if set to 1 will only retrieve the immediate children. If set to 2, will return immediate children plus the children of them, and so on. Setting it to zero will have no effect. A -1 value returns all taxonomy existing levels", defaultValue = "1", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "maxChildren", value = "Max children to return per leaf category. It only limits those children returned in the last level specified by maxLevels. A -1 value means all children are returned.", defaultValue = "-1", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries for the returned categories", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findById(
      version: Int,
      @ApiParam(value = "A category id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean) = ContextAction.async {  implicit context => implicit request =>

    val startTime = System.currentTimeMillis()
    val categoryFacetQuery = new ProductFacetQuery("categoryPath")
      .withAncestorCategory(id)
      .withFilterQueries()
      .withPagination()
    // @todo revisit this limit
    categoryFacetQuery.setFacetLimit(MaxFacetPaginationLimit)

    if(Logger.isDebugEnabled) {
      Logger.debug(s"Searching for child categories for [$id] with query ${categoryFacetQuery.toString}")
    }
    val future = solrServer.query(categoryFacetQuery).flatMap( catalogResponse => {
      val facetFields = catalogResponse.getFacetFields
      var taxonomyFuture: Future[SimpleResult] = null

      if(facetFields != null) {
        facetFields.map( facetField => {
          if("categorypath".equals(facetField.getName.toLowerCase)) {
            Logger.debug(s"Got ${facetField.getValueCount} different category paths for category [$id]")

            val storage = withNamespace(storageFactory)

            if(facetField.getValueCount > 0) {
              val categoryPaths = facetField.getValues.map(facetValue => {facetValue.getName})
              var maxLevels = Integer.parseInt(request.getQueryString("maxLevels").getOrElse("1"))

              //Check if we got -1 as maxLevels.
              if(maxLevels < 0) {
                maxLevels = Int.MaxValue
              }

              val maxChildren = Integer.parseInt(request.getQueryString("maxChildren").getOrElse("-1"))
              taxonomyFuture = categoryService.getTaxonomyForCategory(id, categoryPaths, maxLevels, maxChildren, fieldList(allowStar = true), storage).map( category => {
                if(category != null) {
                  Ok(Json.obj(
                    "metadata" -> Json.obj(
                      "time" -> (System.currentTimeMillis() - startTime)),
                    "category" -> category))
                }
                else {
                  Logger.debug(s"Category [$id] does not exist in storage")
                  NotFound(Json.obj("message" -> s"Cannot retrieve category with id $id"))
                }
              })
            }
          }
        })
      }

      if(taxonomyFuture != null) {
        withErrorHandling(taxonomyFuture, s"Cannot retrieve category with id $id")
      }
      else {
        Future(NotFound(Json.obj("message" -> s"Cannot retrieve category with id $id")))
      }
    })

    withErrorHandling(future, s"Cannot retrieve category with id [$id]")
  }

  @ApiOperation(value = "Searches top level categories for a given site", notes = "Returns top level category information for a given site", response = classOf[Category], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Category not found for site")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "maxLevels", value = "Max taxonomy levels to return. For example, if set to 1 will only retrieve the immediate children. If set to 2, will return immediate children plus the children of them, and so on. Setting it to zero will have no effect. A -1 value returns all taxonomy existing levels", defaultValue = "1", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "maxChildren", value = "Max children to return per leaf category. It only limits those children returned in the last level specified by maxLevels. A -1 value means all children are returned.", defaultValue = "-1", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries for the returned categories", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findBySite(
                version: Int,
                @ApiParam(value = "Site to search", required = true)
                @QueryParam("site")
                site: String,
                @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
                @QueryParam("outlet")
                outlet: Boolean) = ContextAction.async { implicit context => implicit request =>

    val startTime = System.currentTimeMillis()
    val categoryFacetQuery = new ProductFacetQuery("categoryPath")
      .withFacetPrefix(s"$site.")
      .withFilterQueries()
      .withPagination()
    // @todo revisit this limit
    categoryFacetQuery.setFacetLimit(MaxFacetPaginationLimit)
    if(Logger.isDebugEnabled) {
      Logger.debug(s"Searching for top level categories in site [$site] with query ${categoryFacetQuery.toString}")
    }

    val future = solrServer.query(categoryFacetQuery).flatMap( catalogResponse => {
      val facetFields = catalogResponse.getFacetFields
      var taxonomyFuture: Future[SimpleResult] = null

      if(facetFields != null) {
        facetFields.map( facetField => {
          if("categorypath".equals(facetField.getName.toLowerCase)) {
            Logger.debug(s"Got ${facetField.getValueCount} different category paths for site [$site]")

            val storage = withNamespace(storageFactory)

            if(facetField.getValueCount > 0) {
              val categoryPaths = facetField.getValues.map(facetValue => {facetValue.getName})
              var maxLevels = Integer.parseInt(request.getQueryString("maxLevels").getOrElse("1")) + 1  //Plus one because the root category is not returned

              //Check if we got a valid maxLevels parameter.
              if(maxLevels <= 1) {
                maxLevels = Int.MaxValue
              }

              val maxChildren = Integer.parseInt(request.getQueryString("maxChildren").getOrElse("-1"))
              taxonomyFuture = categoryService.getTaxonomyForCategory(site, categoryPaths, maxLevels, maxChildren, fieldList(allowStar = true), storage).map( category => {
                if(category != null) {
                  Ok(Json.obj(
                    "metadata" -> Json.obj(
                      "time" -> (System.currentTimeMillis() - startTime)),
                    "categories" -> category.childCategories))
                }
                else {
                  Logger.debug(s"Category [$site] does not exist in storage")
                  NotFound(Json.obj("message" -> s"Cannot retrieve category with id $site"))
                }
              })
            }
          }
        })
      }

      if(taxonomyFuture != null) {
        withErrorHandling(taxonomyFuture, s"Cannot retrieve top level categories for site $site")
      }
      else {
        Future(NotFound(Json.obj("message" -> s"Cannot retrieve top level categories for site $site")))
      }
    })

    withErrorHandling(future, s"Cannot retrieve category with id [$site]")
  }

  @ApiOperation(value = "Creates categories", notes = "Creates/updates the given categories", httpMethod = "PUT")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "categories", value = "Categories to create/update", required = true, dataType = "org.opencommercesearch.api.models.CategoryList", paramType = "body"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 400, message = "Missing required fields"),
    new ApiResponse(code = 400, message = "Exceeded maximum number of categories that can be created at once")
  ))
  def bulkCreateOrUpdate(version: Int) = ContextAction.async (parse.json(maxLength = 1024 * 2000)) { implicit context => implicit request =>
    Json.fromJson[CategoryList](request.body).map { categoryList =>
      val categories = categoryList.categories
      if (categories.size > MaxUpdateCategoryBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of categories. Maximum is $MaxUpdateCategoryBatchSize")))
      } else {
        try {
          val storage = withNamespace(storageFactory)
          val storageFuture = storage.saveCategory(categories:_*)
          
          val update = withCategoryCollection(new AsyncUpdateRequest())
          val docs = categoryList.toDocuments
          update.add(docs)
          val catalogUpdateFuture = update.process(solrServer)
          val suggestionFuture = IndexableElement.addToIndex(categories filter { category => !category.isRuleBased.getOrElse(false) })

          val future = Future.sequence(List[Future[Any]](storageFuture, catalogUpdateFuture, suggestionFuture)) map { result =>
            Created
          }

          withErrorHandling(future, s"Cannot store categories with ids [${categories map (_.id) mkString ","}]")
      } catch {
          case e: IllegalArgumentException =>
            Logger.error(e.getMessage)
            Future.successful(BadRequest(Json.obj(
              "message" -> e.getMessage
            )))
        }
      }
    }.recover {
      case e: JsError =>
        Logger.error(s"Missing required fields ${JsError.toFlatJson(e)}")
        Future.successful(BadRequest(Json.obj(
          "message" -> "Missing required fields")))

    }.get
  }

  @ApiOperation(value = "Deletes categories", notes = "Deletes categories that were not updated in a given feed", httpMethod = "DELETE")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "preview", value = "Delete categories in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All categories with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long) = ContextAction.async { implicit context => implicit request =>

    val update = withCategoryCollection(new AsyncUpdateRequest())
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
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Suggest categories in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial category name is too short")))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial category name", required = true)
      @QueryParam("q")
      query: String,
      @ApiParam(value = "Site to search", required = true)
      @QueryParam("site")
      site: String) = ContextAction.async { implicit context => implicit request =>
    val solrQuery = withCategoryCollection(new SolrQuery(query))
    findSuggestionsFor("category", query, site)
  }

  @ApiOperation(value = "Return all brands", notes = "Returns all brands for a given category", response = classOf[Brand], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Category not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findBrandsByCategoryId(
     version: Int,
     @ApiParam(value = "A category id", required = true)
     @PathParam("id")
     id: String,
     @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
     @QueryParam("outlet")
     outlet: Boolean) = ContextAction.async { implicit context => implicit request =>

    val startTime = System.currentTimeMillis()
    val categoryFacetQuery = new ProductFacetQuery("brandId")
    if (StringUtils.isNotBlank(id)) {
      Logger.debug(s"Query brands for category Id [$id]")
      categoryFacetQuery.withAncestorCategory(id)
    }
    
    solrServer.query(categoryFacetQuery).flatMap( categoryResponse => {
      //query the SOLR product catalog with the query we generated in the code above.
      val brandFacet = categoryResponse.getFacetField("brandId")
      
      if (brandFacet != null && brandFacet.getValueCount > 0) {
        //if we have results from the product catalog collection, 
        //then generate another SOLR query object to query the brand collection. 
        //The query consists of a bunch of 'OR' statements generated from the brand facet filter elements
        val brandIds = JIterableWrapper(brandFacet.getValues).map(filter =>  filter.getName)
        
	    val storage = withNamespace(storageFactory)
	    val future = storage.findBrands(brandIds, fieldList(allowStar = true)).map( categories => {
		    //now we need to retrieve the actual brand objects from the brand collection  
		    if(categories != null) {
		      Ok(Json.obj(
	                "metadata" -> Json.obj(
	                   "categoryId" -> id,
	                   "found" -> brandIds.size,
	                   "time" -> (System.currentTimeMillis() - startTime)),
	                "brands" -> Json.toJson(categories)
	                //the categories obj is like a sql result set, it's an iterable that can only be read once
	          ))
		    } else {
		      Logger.debug("Category " + id + " not found")
		        NotFound(Json.obj(
		          "message" -> s"Cannot find category with id [$id]"
		      ))
		    }
	    })
	    
	    withErrorHandling(future, s"Cannot find category with id [$id]")

      } else {
        //if the product catalog didn't return filters for the brand facet, then return a null query
        Logger.debug(s"No brands available for category id [$id]")
        Future[SimpleResult](NotFound(Json.obj(
                "message" -> s"No brands available for category id [$id]"
        )))
      } 
    })
  }
}
 
