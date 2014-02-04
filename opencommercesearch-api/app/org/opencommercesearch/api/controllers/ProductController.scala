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
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import java.util

import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue

import org.opencommercesearch.api.models._
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.service.CategoryService
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.response.{UpdateResponse, QueryResponse}
import org.apache.solr.client.solrj.SolrQuery
import com.wordnik.swagger.annotations._
import javax.ws.rs.{QueryParam, PathParam}
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.lang3.StringUtils
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.common.{FilterQuery, FacetHandler}
import org.apache.solr.client.solrj.util.ClientUtils
import java.net.URLDecoder

@Api(value = "products", basePath = "/api-docs/products", description = "Product API endpoints")
object ProductController extends BaseController {

  val Score = "score"
  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Searches products", notes = "Returns product information for a given product", response = classOf[Product], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Product not found")))
  @ApiImplicitParams(value = Array(
    //new ApiImplicitParam(name = "offset", value = "Offset in the SKU list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    //new ApiImplicitParam(name = "limit", value = "Maximum number of SKUs", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "categoryFields", value = "Comma delimited field list for categories. Ignored if parameter taxonomy is false.", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
    version: Int,
    @ApiParam(value = "A product id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(value = "Site to search for SKUs", required = false)
    @QueryParam("site")
    site: String,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
    @QueryParam("preview")
    preview: Boolean,
    @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display category taxonomy for the product", required = false)
    @QueryParam("preview")
    taxonomy: Boolean) = Action.async { implicit request =>

    Logger.debug(s"Query product $id")

    val startTime = System.currentTimeMillis()
    val storage = withNamespace(storageFactory, preview)
    var productFuture: Future[Product] = null

    if (site != null) {
      productFuture = storage.findProduct(id, site, country(request.acceptLanguages), fieldList())
    } else {
      productFuture = storage.findProduct(id, country(request.acceptLanguages), fieldList())
    }

    val future = productFuture flatMap { product =>
      if (product != null) {
        if(taxonomy) {
          val catalogQuery = withSearchCollection(withFieldFacet("ancestorCategoryId", withFacetPagination(new SolrQuery("*:*"))), preview)
          if(site != null) {
            catalogQuery.addFilterQuery(s"categoryPath:$site")
          }

          catalogQuery.addFilterQuery("productId:" + id)

          if(Logger.isDebugEnabled) {
            Logger.debug("Searching for category ids for product with query " + catalogQuery.toString)
          }

          solrServer.query(catalogQuery).flatMap(response => {
            val facetFields = response.getFacetFields
            var taxonomyFuture: Future[SimpleResult] = null

            if(facetFields != null) {
              facetFields.map( facetField => {
                if("ancestorcategoryid".equals(facetField.getName.toLowerCase)) {
                  if(Logger.isDebugEnabled) {
                    Logger.debug(s"Got ${facetField.getValueCount} category ids for product $id")
                  }

                  val storage = withNamespace(storageFactory, preview)

                  if(facetField.getValueCount > 0) {
                    val categoryIds = facetField.getValues.map(facetValue => {facetValue.getName})
                    Logger.debug(s"Category ids for product $id are $categoryIds")

                    val categoryFuture = categoryService.getTaxonomy(categoryIds, fieldList(allowStar = true, fieldsFieldName = "categoryFields"), storage)

                    taxonomyFuture = categoryFuture.map(categoryTaxonomy => {
                      Ok(Json.obj(
                        "metadata" -> Json.obj(
                          "found" -> 1,
                          "time" -> (System.currentTimeMillis() - startTime)),
                        "product" -> Json.toJson(product),
                        "categories" -> Json.toJson(facetField.getValues.map( facetValue => {categoryTaxonomy(facetValue.getName)}))))
                    })
                  }
                  else {
                    Logger.debug(s"Got an empty ancestorCategoryId field facet for product $id")
                  }
                }
                else {
                  Logger.debug(s"Cannot get categories for product $id because there are no field facets in Solr response")
                }
              })
            }
            else {
              Logger.debug(s"Got 0 categories for product $id, no facets were returned")
            }

            if(taxonomyFuture != null) {
              withErrorHandling(taxonomyFuture, s"Found categories for brand $id, but could not resolve taxonomy")
            }
            else {
              Future(NotFound(Json.obj("message" -> s"No categories found for $id")))
            }
          })
        }
        else {
          Future(Ok(Json.obj(
            "metadata" -> Json.obj(
              "found" -> 1,
              "time" -> (System.currentTimeMillis() - startTime)),
            "product" -> Json.toJson(product))))
        }
      } else {
        Logger.debug("Product " + id + " not found")
        Future(NotFound(Json.obj(
          "messages" -> s"Cannot find product with id [$id]"
        )))
      }
    }

    withErrorHandling(future, s"Cannot retrieve product with id [$id]")
  }

  /**
   * Helper method to process search results
   * @param groupSummary returned by Solr
   * @return an array for products containing its summary
   */
  private def processGroupSummary(groupSummary: NamedList[Object]) : JsArray = {
    val groups = ArrayBuffer[JsObject]()
    var productSummaries = groupSummary.get("productId").asInstanceOf[NamedList[Object]];
    if(productSummaries != null) {
     JIterableWrapper(productSummaries).map(productSummary => {
       var parameterSummaries = productSummary.getValue.asInstanceOf[NamedList[Object]];
       val productSeq = ArrayBuffer[(String,JsValue)]()
       JIterableWrapper(parameterSummaries).map(parameterSummary => {
         var statSummaries = parameterSummary.getValue.asInstanceOf[NamedList[Object]];
         val parameterSeq = ArrayBuffer[(String,JsString)]()
         JIterableWrapper(statSummaries).map(statSummary => {
           parameterSeq += ((statSummary.getKey, new JsString(statSummary.getValue.toString)))
         })
         productSeq += ((parameterSummary.getKey, new JsObject(parameterSeq)))
       })
       groups += new JsObject(ArrayBuffer[(String,JsValue)]((productSummary.getKey, new JsObject(productSeq))))
     })
    }
    new JsArray(groups);
  }


  /**
   * Helper method to process search results
   * @param q the Solr query
   * @param response the Solr response
   * @return a tuple with the total number of products found and the list of product documents in the response
   */
  private def processSearchResults[R](q: String, response: QueryResponse)(implicit req: Request[R]) : Future[(Int, Iterable[Product], NamedList[Object])] = {
    val groupResponse = response.getGroupResponse
    val groupSummary = response.getResponse.get("groups_summary").asInstanceOf[NamedList[Object]];

    if (groupResponse != null) {
      val commands = groupResponse.getValues

      if (commands.size > 0) {
        val command = groupResponse.getValues.get(0)
        val products = new util.ArrayList[(String, String)]
        if ("productId".equals(command.getName)) {
          if (command.getNGroups > 0) {
            for (group <- JIterableWrapper(command.getValues)) {
              val documentList = group.getResult
              val product  = documentList.get(0)
              product.setField("productId", group.getGroupValue)
              products.add((group.getGroupValue, product.getFieldValue("id").asInstanceOf[String]))
            }
            val storage = withNamespace(storageFactory, preview = true)
            storage.findProducts(products, country(req.acceptLanguages), fieldList()).map( products => {
              (command.getNGroups, products, groupSummary)
            })
          } else {
            Future.successful((0, null, null))
          }
        } else {
          Logger.debug(s"Unexpected response found for query $q")
          Future.successful((0, null, null))
        }
      } else {
        Logger.debug(s"Unexpected response found for query $q")
        Future.successful((0, null, null))
      }
    } else {
      Future.successful((0, null, null))
    }
  }

  @ApiOperation(value = "Searches products", notes = "Returns products for a given query", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query")
  ))
  def search(
      version: Int,
      @ApiParam(value = "Search term", required = true)
      @QueryParam("q")
      q: String,
      @ApiParam(value = "Site to search", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean, 
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean) = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    val query = withDefaultFields(withSearchCollection(withPagination(new SolrQuery(q)), preview))

    Logger.debug("Searching for " + q)
    query.setFacet(true)
    query.set("siteId", site)
    query.set("pageType", "search") // category or rule
    query.set("rule", true)
    
    val filterQueries = FilterQuery.parseFilterQueries(URLDecoder.decode(request.getQueryString("filterQueries").getOrElse(""), "UTF-8"))
    initQueryParams(query, site, showOutletProducts = true, "search")
    
    filterQueries.foreach(fq => {
       query.add("rule.fq", fq.toString)
    })

    val future: Future[SimpleResult] = solrServer.query(query).flatMap( response => {
      if (query.getRows > 0) {
        processSearchResults(q, response).map { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "productSummary" -> processGroupSummary(groupSummary),
                  "time" -> (System.currentTimeMillis() - startTime),
                  "facets" -> buildFacets(response, query, filterQueries)),
                "products" -> Json.toJson(
                  products map (Json.toJson(_))
                )))
            } else {
              Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> (System.currentTimeMillis() - startTime)),
                  "productSummary" -> processGroupSummary(groupSummary),
                "products" -> Json.arr()
              ))
            }
          } else {
            Logger.debug(s"Unexpected response found for query $q")
            InternalServerError(Json.obj(
              "metadata" -> Json.obj(
                "time" -> (System.currentTimeMillis() - startTime)),
              "message" -> "Unable to execute query"))
          }
        }
      } else {
        Future.successful(Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> response.getResults.getNumFound),
            "time" -> (System.currentTimeMillis() - startTime))))
      }
    })

    withErrorHandling(future, s"Cannot search for [$q]")
  }

  @ApiOperation(value = "Browses brand products ", notes = "Returns products for a given brand", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query")
  ))
  def browseBrand(
      version: Int,
      @ApiParam(value = "Site to browse", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(value = "Brand to browse", required = true)
      @PathParam("brandId")
      brandId: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    Logger.debug(s"Browsing brand $brandId")
    withErrorHandling(doBrowse(version, null, site, brandId, isOutlet = false, preview, "category"), s"Cannot browse brand [$brandId]")
  }

  @ApiOperation(value = "Browses brand's category products ", notes = "Returns products for a given brand category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query")
  ))
  def browseBrandCategory(
      version: Int,
      @ApiParam(value = "Site to browse", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(value = "Brand to browse", required = true)
      @PathParam("brandId")
      brandId: String,
      @ApiParam(value = "Category to browse", required = true)
      @PathParam("categoryId")
      categoryId: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    Logger.debug(s"Browsing category $categoryId for brand $brandId")
    withErrorHandling(doBrowse(version, categoryId, site, brandId, isOutlet = false, preview, "category"), s"Cannot browse brand category [$brandId - $categoryId]")
  }

  @ApiOperation(value = "Browses category products ", notes = "Returns products for a given category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query")
  ))
  def browse(
      version: Int,
      @ApiParam(value = "Site to browse", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(value = "Category to browse", required = true)
      @PathParam("id")
      categoryId: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    Logger.debug(s"Browsing $categoryId")
    withErrorHandling(doBrowse(version, categoryId, site, null, outlet, preview, "category"), s"Cannot browse category [$categoryId]")
  }

  private def doBrowse(version: Int, categoryId: String, site: String, brandId: String, isOutlet: Boolean,
                       preview: Boolean, requestType: String)(implicit request: Request[AnyContent]) = {
    val startTime = System.currentTimeMillis()
    val query = withDefaultFields(withSearchCollection(withPagination(new SolrQuery("*:*")), preview))

    val filterQueries = FilterQuery.parseFilterQueries(URLDecoder.decode(request.getQueryString("filterQueries").getOrElse(""), "UTF-8"))

    query.setFacet(true)
    initQueryParams(query, site, showOutletProducts = isOutlet, requestType)

    filterQueries.foreach(fq => {
       query.add("rule.fq", fq.toString)
    })

    val storage = withNamespace(storageFactory, preview)
    storage.findCategory(categoryId, Seq("hierarchyTokens", "ruleFilters", "isRuleBased")).flatMap { category =>
      
      var isRulePage = false
      if (category != null && category.isRuleBased.get) {
        //set the rules expression filter query for rule based categories
        val localeKey = language(request.acceptLanguages)+"_"+country(request.acceptLanguages)
        val ruleFilters = category.ruleFilters.getOrElse(Seq.empty[String]).filter(rule => {
            rule.startsWith(localeKey)
        })
        for (rules <- ruleFilters) {
          if(rules.nonEmpty) {
            val str = rules.substring(rules.indexOf(":") + 1, rules.length())
            query.addFilterQuery(rules.substring(rules.indexOf(":") + 1, rules.length()));
          }
        }
        query.setParam("rulePage", true)
        query.setParam("q", "*:*")
        isRulePage = true
      } else {
        //otherwise handle a regular category or a brand category
        if (StringUtils.isNotBlank(categoryId)) {
          query.addFilterQuery(s"ancestorCategoryId:$categoryId")
        }
        if (StringUtils.isNotBlank(brandId)) {
          query.addFilterQuery(s"brandId:$brandId")
          query.setParam("brandId", brandId)
        }
      }
          
      if (category != null) {
        for (tokens <- category.hierarchyTokens) {
          if (tokens.nonEmpty) {
            val token = tokens.get(0)
            //we need to split the first part which is the level of the tokens.
            //i.e.  2.site.category.subcategory
            if(token.substring(token.indexOf(".") + 1).startsWith(site)) {
              val escapedCategoryFilter = ClientUtils.escapeQueryChars(token)
              query.add("categoryFilter", escapedCategoryFilter)
              if(!isRulePage) {
                //if we are in a rule based category, solr won't have the category indexed, so we need to avoid this request
                query.addFilterQuery("category:" + escapedCategoryFilter)
              }
            }
          }
        }
      }

      solrServer.query(query).flatMap { response =>
        val groupResponse = response.getGroupResponse

        if (groupResponse != null) {
          val commands = groupResponse.getValues

          if (commands.size > 0) {
            val command = groupResponse.getValues.get(0)

            if ("productId".equals(command.getName)) {
              if (command.getNGroups > 0) {
                val products = new util.ArrayList[(String, String)]
                for (group <- command.getValues.toSeq) {
                  val documentList = group.getResult
                  val product = documentList.get(0)
                  product.setField("productId", group.getGroupValue)
                  products.add((group.getGroupValue, product.getFieldValue("id").asInstanceOf[String]))
                }
                val storage = withNamespace(storageFactory, preview = true)
                storage.findProducts(products, country(request.acceptLanguages), fieldList()).map(products => {
                  Ok(Json.obj(
                    "metadata" -> Json.obj(
                      "found" -> command.getNGroups.intValue(),
                      "time" -> (System.currentTimeMillis() - startTime),
                      "facets" -> buildFacets(response, query, filterQueries)),
                    "products" -> Json.toJson(
                      products map (Json.toJson(_)))))
                })
              } else {
                Future.successful(Ok(Json.obj(
                  "metadata" -> Json.obj(
                    "found" -> command.getNGroups.intValue(),
                    "time" -> (System.currentTimeMillis() - startTime)),
                  "products" -> Json.arr())))
              }
            } else {
              Logger.debug(s"Unexpected response found for category $categoryId")
              Future.successful(InternalServerError(Json.obj(
                "message" -> "Unable to execute query")))
            }
          } else {
            Logger.debug(s"Unexpected response found for category $categoryId")
            Future.successful(InternalServerError(Json.obj(
              "message" -> "Unable to execute query")))
          }
        } else {
          Logger.debug(s"Unexpected response found for category $categoryId")
          Future.successful(InternalServerError(Json.obj(
            "message" -> "Unable to execute query")))
        }
      }
    }
  }


  private def buildFacets(response: QueryResponse, query: SolrQuery, filterQueries: Array[FilterQuery]) : Seq[Facet] = {
    var facetData = Seq.empty[NamedList[AnyRef]]

    if (response.getResponse != null && response.getResponse.get("rule_facets") != null) {
      facetData = response.getResponse.get("rule_facets").asInstanceOf[util.ArrayList[NamedList[AnyRef]]]
    }
    new FacetHandler(query, response, filterQueries, facetData).getFacets
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action.async (parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>
      val products = productList.products
      if (products.size > MaxUpdateProductBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxUpdateProductBatchSize")))
      } else {
        try {
          val (_, skuDocs) = productList.toDocuments(categoryService, preview)
          if(skuDocs.isEmpty) {
              Future.successful(BadRequest(Json.obj(
              "message" -> "Can't save an empty sku list. Check that the required fields of the products are set")))
          } else {
            val storage = withNamespace(storageFactory, preview)
            val productFuture = storage.saveProduct(products:_*)
            val searchUpdate = withSearchCollection(new AsyncUpdateRequest(), preview)
            searchUpdate.add(skuDocs)
            val searchFuture: Future[UpdateResponse] = searchUpdate.process(solrServer)
            val future: Future[SimpleResult] = productFuture zip searchFuture map { case (r1, r2) =>
              Created
            }

            withErrorHandling(future, s"Cannot store products with ids [${products map (_.id.get) mkString ","}]")
          }
        } catch {
          case e: IllegalArgumentException =>
            Logger.error(e.getMessage)
            Future.successful(BadRequest(Json.obj(
              "message" -> e.getMessage)))
        }
      }
    }.recoverTotal {
      case e: JsError =>
        Future.successful(BadRequest(Json.obj(
          // @TODO figure out how to pull missing field from JsError
          "message" -> "Missing required fields")))
    }
  }

  @ApiOperation(value = "Deletes products", notes = "Deletes products that were not updated in a given feed", httpMethod = "DELETE")
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All products with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete categories in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    val update = withSearchCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-indexStamp:" + feedTimestamp)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })


    withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
  }

  @ApiOperation(value = "Deletes products", notes = "Deletes the given product", httpMethod = "DELETE")
  def deleteById(
      version: Int = 1,
      @ApiParam(value = "A product id", required = false)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete categories in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    val update = withSearchCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("productId:" + id)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })

    withErrorHandling(future, s"Cannot delete product [$id  ]")
  }

  @ApiOperation(value = "Suggests products", notes = "Returns product suggestions for given partial product title", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "id,title", required = false, dataType = "string", paramType = "query")
  ))
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial product title is too short")))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial product title", required = true)
      @QueryParam("q")
      q: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    val solrQuery = withProductCollection(new SolrQuery(q), preview)
    findSuggestionsFor(classOf[Product], "products" , solrQuery)
  }

  /**
   * Helper method to initialize common parameters
   * @param query is the solr query
   * @param site is the site where we are searching
   * @param showOutletProducts indicates if outlet product should be return or not
   * @param request is the implicit request
   * @return the solr query
   */
  private def initQueryParams(query: SolrQuery, site: String, showOutletProducts: Boolean, requestType: String)(implicit request: Request[AnyContent]) : SolrQuery = {
    if (query.get("facet") != null && query.getBool("facet")) {
      query.addFacetField("category")
      query.set("facet.mincount", 1)
    }

    query.set("rule", true)
    query.set("siteId", site)
    // @todo add to API interface
    query.set("catalogId", site)
    if (requestType != null) {
      query.set("pageType", requestType)
    }

    if ((query.getRows != null && query.getRows > 0) && (query.get("group") == null || query.getBool("group"))) {
      initQueryGroupParams(query)
    } else {
      query.remove("groupcollapse")
      query.remove("groupcollapse.fl")
      query.remove("groupcollapse.ff")
    }
    
    if(showOutletProducts) {
        if (request.getQueryString("outlet").getOrElse("false").toBoolean ) {
            query.addFilterQuery("isOutlet:true")
        }
    } else {
       // Hide any outlet items for non-search PLPs
       query.addFilterQuery("isOutlet:false")
    }
    query.addFilterQuery("isRetail:true")
    
    initQuerySortParams(query)
    query
  }

  /**
   * Helper method to initialize group parameters
   * @param query is the solr query
   * @param request is the implicit request
   * @return
   */
  private def initQueryGroupParams(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    query.set("group", true)
      .set("group.ngroups", true)
      .set("group.limit", 50)
      .set("group.field", "productId")
      .set("group.facet", false)

      val clauses: util.List[SolrQuery.SortClause] = query.getSorts
      var isSortByScore: Boolean = false
      if (clauses.size > 0) {
        import scala.collection.JavaConversions._
        for (clause <- clauses) {
          if (Score.equals(clause.getItem)) {
            isSortByScore = true
          }
        }
      }
      else {
        isSortByScore = true
      }
      if (isSortByScore) {
        query.set("group.sort", "isCloseout asc, score desc, sort asc")
      }
    query
  }

  /**
   * Helper method to initialize the sorting specs
   * @param query is the solr query
   * @param request is the implicit request
   * @return
   */
  private def initQuerySortParams(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    for (sort <- request.getQueryString("sort")) {
      val sortSpecs = sort.split(",")
      if (sortSpecs != null && sortSpecs.length > 0) {
        val country_ = country(request.acceptLanguages)
        for (sortSpec <- sortSpecs) {
          val selectedOrder = if (sortSpec.trim.endsWith(" asc")) SolrQuery.ORDER.asc else SolrQuery.ORDER.desc

          if (sortSpec.indexOf("discountPercent") != -1) {
            query.addSort(s"discountPercent$country_", selectedOrder)
          }
          if (sortSpec.indexOf("reviewAverage") != -1) {
            query.addSort("bayesianReviewAverage", selectedOrder)
          }
          if (sortSpec.indexOf("price") != -1) {
            query.addSort(s"salePrice$country_", selectedOrder)
          }
        }
      }
    }
    query
  }

  /**
   * Helper method to set the fields to return for each product
   *
   * @param query is the solr query
   * @param request is the implicit request
   * @return
   */
  private def withDefaultFields(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    val country_ = country(request.acceptLanguages)
    val listPrice = s"listPrice$country_"
    val salePrice =  s"salePrice$country_"
    val discountPercent = s"discountPercent$country_"

    query.addFilterQuery(s"country:$country_")
    query.setParam("groupcollapse", true)
    query.setParam("groupcollapse.fl", s"$listPrice,$salePrice,$discountPercent")
    query.setParam("groupcollapse.ff", "isCloseout")

    query.setFields("id")
  }
}
