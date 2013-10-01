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

import java.util

import org.opencommercesearch.api.models._
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.service.CategoryService
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.response.{UpdateResponse, QueryResponse}
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.SolrDocument
import org.apache.commons.lang3.StringUtils
import com.wordnik.swagger.annotations._
import javax.ws.rs.{QueryParam, PathParam}

import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.convert.Wrappers.JListWrapper

@Api(value = "/products", listingPath = "/api-docs/products", description = "Product API endpoints")
object ProductController extends BaseController {

  val Score = "score"
  val categoryService = new CategoryService(solrServer)

  @ApiOperation(value = "Searches products", notes = "Returns product information for a given product", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiErrors(value = Array(new ApiError(code = 404, reason = "Product not found")))
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the SKU list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of SKUs", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query")
  ))
  def findById(
      version: Int,
      @ApiParam(value = "A product id", required = true)
      @PathParam("id")
      id: String,
      @ApiParam(value = "Site to search for SKUs", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val startTime = System.currentTimeMillis()
    val fields = request.getQueryString("fields")
    val searchSkus = fields.isEmpty || fields.get.indexOf("skus") != -1

    val query = withProductCollection(withFields(new SolrQuery(), fields), preview)

    Logger.debug(s"Query product $id")

    query.set("id", id)
    query.setRequestHandler(RealTimeRequestHandler)

    val productFuture = solrServer.query(query).map( response => {
      val doc = response.getResponse.get("doc")
      if (doc != null) {
        solrServer.binder.getBean(classOf[Product], doc.asInstanceOf[SolrDocument])
      } else {
        null
      }
    })

    val productIdQuery = s"productId:$id"
    var skuFuture: Future[(Int, util.List[Product])] = null

    if (searchSkus) {
      val skuQuery = withDefaultFields(withSearchCollection(withPagination(new SolrQuery(productIdQuery)), preview), site, None)
      skuQuery.set("group", false)
      skuQuery.setFacet(false)
      initQueryParams(skuQuery, site, showCloseoutProducts = true, null)

      skuFuture = solrServer.query(skuQuery).map( response => {
        processSearchResults(productIdQuery, response)
      })
    } else {
      skuFuture = Future.successful((0, null))
    }

    val future = productFuture zip skuFuture map { case (product, (found, products)) =>
      if (product != null) {
        if (products != null) {
          product.skus = Option.apply(JIterableWrapper(products).toSeq.map( p => p.skus.get(0) ))
        }
        Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> found,
            "time" -> (System.currentTimeMillis() - startTime)),
          "product" -> Json.toJson(product)))
      } else {
        Logger.debug("Product " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find product with id [$id]"
        ))
      }
    }

    Async {
      withErrorHandling(future, s"Cannot retrieve product with id [$id]")
    }
  }

  /**
   * Helper method to process search results
   * @param q the Solr query
   * @param response the Solr response
   * @return a tuple with the total number of SKUs found and the list of SKUs in the response
   */
  private def processSearchResults(q: String, response: QueryResponse) : (Int, util.List[Product]) = {
    val groupResponse = response.getGroupResponse
    if (groupResponse != null) {
      val commands = groupResponse.getValues

      if (commands.size > 0) {
        val command = groupResponse.getValues.get(0)
        val products = new util.ArrayList[Product]
        if ("productId".equals(command.getName)) {
          if (command.getNGroups > 0) {
            for (group <- JIterableWrapper(command.getValues)) {
              val documentList = group.getResult
              val product = solrServer.binder.getBean(classOf[Product], documentList.get(0))
              product.skus = Option.apply(JIterableWrapper(solrServer.binder.getBeans(classOf[Sku], documentList)).toSeq)
              products.add(product)
            }
            (command.getNGroups, products)
          } else {
            (0, util.Collections.emptyList())
          }
        } else {
          Logger.debug(s"Unexpected response found for query $q")
          (0, null)
        }
      } else {
        Logger.debug(s"Unexpected response found for query $q")
        (0, null)
      }
    } else {
      val documentList = response.getResults
      val products = new util.ArrayList[Product]

      // @todo optimize. Each sku is wrapped into its own product. This code is only called
      // when calling findById to parse the product skus
      for (doc <- JIterableWrapper(documentList)) {
        val product = solrServer.binder.getBean(classOf[Product], documentList.get(0))
        product.skus = Option.apply(Seq(solrServer.binder.getBean(classOf[Sku], doc)))
        products.add(product)
      }
      (documentList.getNumFound.toInt, products)
    }
  }

  @ApiOperation(value = "Searches products", notes = "Returns products for a given query", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query")
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
      preview: Boolean) = Action { implicit request =>
    val startTime = System.currentTimeMillis()
    val query = withDefaultFields(withSearchCollection(withPagination(new SolrQuery(q)), preview), site, request.getQueryString("fields"))

    Logger.debug("Searching for " + q)
    query.setFacet(true)
    query.set("siteId", site)
    query.set("pageType", "search") // category or rule
    query.set("rule", true)
    initQueryParams(query, site, showCloseoutProducts = true, "search")

    val future = solrServer.query(query).map( response => {
      if (query.getRows > 0) {
        val (found, skus) = processSearchResults(q, response)
        if (skus != null) {
          if (skus.size() > 0) {
            Ok(Json.obj(
              "metadata" -> Json.obj(
                "found" -> found,
                "time" -> (System.currentTimeMillis() - startTime)),
              "products" -> Json.arr(
                JListWrapper(skus) map (Json.toJson(_))
              )))
          } else {
            Ok(Json.obj(
              "metadata" -> Json.obj(
                "found" -> found,
                "time" -> (System.currentTimeMillis() - startTime)),
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
      } else {
        Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> response.getResults.getNumFound),
            "time" -> (System.currentTimeMillis() - startTime)))
      }
    })

    Async {
      withErrorHandling(future, s"Cannot search for [$q]")
    }
  }

  @ApiOperation(value = "Browses brand products ", notes = "Returns products for a given brand", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query")
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
      preview: Boolean) = Action { implicit request =>
    Logger.debug(s"Browsing brand $brandId")
    Async {
      withErrorHandling(doBrowse(version, null, site, brandId, closeout = false, null, preview, "brand"), s"Cannot browse brand [$brandId]")
    }
  }

  @ApiOperation(value = "Browses brand's category products ", notes = "Returns products for a given brand category", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query")
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
      preview: Boolean) = Action { implicit request =>
    Logger.debug(s"Browsing category $categoryId for brand $brandId")
    Async {
      withErrorHandling(doBrowse(version, categoryId, site, brandId, closeout = false, null, preview, "category"), s"Cannot browse brand category [$brandId - $categoryId]")
    }
  }

  @ApiOperation(value = "Browses category products ", notes = "Returns products for a given category", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query")
  ))
  def browse(
      version: Int,
      @ApiParam(value = "Site to browse", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(value = "Category to browse", required = true)
      @PathParam("id")
      categoryId: String,
      ruleFilter: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display closeout results", required = false)
      @QueryParam("closeout")
      closeout: Boolean,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    Logger.debug(s"Browsing $categoryId")
    Async {
      withErrorHandling(doBrowse(version, categoryId, site, null, closeout, ruleFilter, preview, "category"), s"Cannot browse category [$categoryId]")
    }
  }

  private def doBrowse(version: Int, categoryId: String, site: String, brandId: String, closeout: Boolean, ruleFilter: String,
                       preview: Boolean, requestType: String)(implicit request: Request[AnyContent]) = {
    val startTime = System.currentTimeMillis()
    val query = withDefaultFields(withSearchCollection(withPagination(new SolrQuery("*:*")), preview), site, request.getQueryString("fields"))

    if (ruleFilter != null) {
      // @todo handle rule based pages
      query.set("q.alt", ruleFilter)
      query.setParam("q", "")
    } else {
      if (StringUtils.isNotBlank(categoryId)) {
        query.addFilterQuery(s"ancestorCategoryId:$categoryId")
      }

      if (StringUtils.isNotBlank(brandId)) {
        query.addFilterQuery(s"brandId:$brandId")
      }
    }

    query.setFacet(true)
    initQueryParams(query, site, showCloseoutProducts = closeout, requestType)

    solrServer.query(query).map( response => {
      val groupResponse = response.getGroupResponse

      if (groupResponse != null) {
        val commands = groupResponse.getValues

        if (commands.size > 0) {
          val command = groupResponse.getValues.get(0)

          if ("productId".equals(command.getName)) {
            if (command.getNGroups > 0) {
              val allProducts = new util.ArrayList[Product]
              for (group <- JIterableWrapper(command.getValues)) {
                val documentList = group.getResult
                val skus = solrServer.binder.getBeans(classOf[Sku], documentList)
                if (skus.size() > 0) {
                  val doc = documentList.get(0)
                  val product = solrServer.binder.getBean(classOf[Product], doc)
                  product.setId(doc.getFieldValue("productId").asInstanceOf[String])
                  product.setSkus(JIterableWrapper(skus).toSeq)
                  allProducts.add(product)
                }
              }
              Ok(Json.obj(
                "metadata" -> Json.obj(
                   "found" -> command.getNGroups.intValue(),
                   "time" -> (System.currentTimeMillis() - startTime)),
                "products" -> Json.arr(
                  JListWrapper(allProducts) map (Json.toJson(_))
                )))
            } else {
              Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> command.getNGroups.intValue(),
                  "time" -> (System.currentTimeMillis() - startTime)),
                "products" -> Json.arr()
              ))
            }
          } else {
            Logger.debug(s"Unexpected response found for category $categoryId")
            InternalServerError(Json.obj(
              "message" -> "Unable to execute query"
            ))
          }
        } else {
          Logger.debug(s"Unexpected response found for category $categoryId")
          InternalServerError(Json.obj(
            "message" -> "Unable to execute query"
          ))
        }
      } else {
        Logger.debug(s"Unexpected response found for category $categoryId")
        InternalServerError(Json.obj(
          "message" -> "Unable to execute query"
        ))
      }
    })
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action(parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>
      val products = productList.products
      if (products.size > MaxUpdateProductBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxUpdateProductBatchSize"))
      } else {
        try {
          val (productDocs, skuDocs) = productList.toDocuments(categoryService, preview)
          val productUpdate = withProductCollection(new AsyncUpdateRequest(), preview)
          productUpdate.add(productDocs)
          val productFuture: Future[UpdateResponse] = productUpdate.process(solrServer)
          val searchUpdate = withSearchCollection(new AsyncUpdateRequest(), preview)
          searchUpdate.add(skuDocs)
          val searchFuture: Future[UpdateResponse] = searchUpdate.process(solrServer)
          val future: Future[Result] = productFuture zip searchFuture map { case (r1, r2) =>
            Created
          }

          Async {
            withErrorHandling(future, s"Cannot store products with ids [${products map (_.id.get) mkString ","}]")
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
          // @TODO figure out how to pull missing field from JsError
          "message" -> "Missing required fields"))
      }

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
      preview: Boolean) = Action { implicit request =>
    val update = withSearchCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-indexStamp:" + feedTimestamp)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
    }
  }

  @ApiOperation(value = "Deletes products", notes = "Deletes the given product", httpMethod = "DELETE")
  def deleteById(
      version: Int = 1,
      @ApiParam(value = "A product id", required = false)
      @PathParam("id")
      id: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Delete categories in preview", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val update = withSearchCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("productId:" + id)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product [$id  ]")
    }
  }

  @ApiOperation(value = "Suggests products", notes = "Returns product suggestions for given partial product title", responseClass = "org.opencommercesearch.api.models.Product", httpMethod = "GET")
  @ApiParamsImplicit(value = Array(
    new ApiParamImplicit(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiParamImplicit(name = "fields", value = "Comma delimited field list", defaultValue = "id,title", required = false, dataType = "string", paramType = "query")
  ))
  @ApiErrors(value = Array(new ApiError(code = 400, reason = "Partial product title is too short")))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial product title", required = true)
      @QueryParam("q")
      q: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action { implicit request =>
    val solrQuery = withProductCollection(new SolrQuery(q), preview)

    Async {
      findSuggestionsFor(classOf[Product], "products" , solrQuery)
    }
  }

  /**
   * Helper method to initialize common parameters
   * @param query is the solr query
   * @param site is the site where we are searching
   * @param showCloseoutProducts indicates if closeout product should be return or not
   * @param request is the implicit request
   * @return the solr query
   */
  private def initQueryParams(query: SolrQuery, site: String, showCloseoutProducts: Boolean, requestType: String)(implicit request: Request[AnyContent]) : SolrQuery = {
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

    val closeout = request.getQueryString("closeout").getOrElse("true")
    if ("true".equals(closeout)) {
      query.addFilterQuery("isRetail:true")

      if (showCloseoutProducts) {
        query.addFilterQuery("isCloseout:true")
        if (!"search".equals(requestType)) {
          val country_ = country(request.acceptLanguages)
          query.addFilterQuery("onsale" + country_ + ":true")
        }
      } else {
        query.addFilterQuery("isCloseout:" + false)
      }
    }

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
   * @param site is the site where we are searching
   * @param fields is the requested list of fields. If empty, use a default list of fields
   * @param request is the implicit request
   * @return
   */
  private def withDefaultFields(query: SolrQuery, site: String, fields: Option[String])(implicit request: Request[AnyContent]) : SolrQuery = {
    val country_ = country(request.acceptLanguages)
    val listPrice = s"listPrice$country_"
    val salePrice =  s"salePrice$country_"
    val discountPercent = s"discountPercent$country_"

    query.addFilterQuery(s"country:$country_")
    query.setParam("groupcollapse", true)
    query.setParam("groupcollapse.fl", s"$listPrice,$salePrice,$discountPercent")
    query.setParam("groupcollapse.ff", "isCloseout")

    if (fields.isEmpty || fields.get.size <= 0) {
      query.setFields("id", "productId", "title", "brand", "isToos", listPrice, salePrice, discountPercent, "url" + country_,
         "bayesianReviewAverage", "reviews", "isPastSeason", "freeGift" + site, "image", "isCloseout")
      query
    } else {
      withFields(query, fields)
    }
  }
}
