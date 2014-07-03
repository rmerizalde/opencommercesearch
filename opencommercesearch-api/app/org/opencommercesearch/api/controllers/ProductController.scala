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

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc._

import scala.collection.JavaConversions._
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

import javax.ws.rs.{PathParam, QueryParam}

import java.util

import org.opencommercesearch.api._
import org.opencommercesearch.api.Collection._
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.common.{FacetHandler, FilterQuery}
import org.opencommercesearch.api.models._
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.suggester.IndexableElement

import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.{QueryResponse, UpdateResponse}
import org.apache.solr.client.solrj.util.ClientUtils
import org.apache.solr.common.util.NamedList

import com.wordnik.swagger.annotations._

@Api(value = "products", basePath = "/api-docs/products", description = "Product API endpoints")
object ProductController extends BaseController {
  val categoryService = new CategoryService(solrServer, storageFactory)

  @ApiOperation(value = "Searches products", notes = "Returns product information for a given product", response = classOf[Product], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Product not found")))
  @ApiImplicitParams(value = Array(
    //new ApiImplicitParam(name = "offset", value = "Offset in the SKU list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    //new ApiImplicitParam(name = "limit", value = "Maximum number of SKUs", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findById(
    version: Int,
    @ApiParam(value = "A product id", required = true)
    @PathParam("id")
    id: String,
    @ApiParam(value = "Site to search for SKUs", required = false)
    @QueryParam("site")
    site: String) = ContextAction.async { implicit context => implicit request =>

    Logger.debug(s"Query product $id")

    val startTime = System.currentTimeMillis()
    val storage = withNamespace(storageFactory)
    var productFuture: Future[Iterable[Product]] = null
    val productIds = StringUtils.split(id, ",").map(i => (i, null))

    val fields = fieldList(allowStar = true)

    if (site != null) {
      productFuture = storage.findProducts(productIds, site, context.lang.country, fields, minimumFields = false)
    } else {
      productFuture = storage.findProducts(productIds, context.lang.country, fields, minimumFields = false)
    }

    val future = productFuture flatMap { productList =>

      if (productList != null) {

        //Check if we should include the category taxonomy or not
        val includeTaxonomy = fields.isEmpty || fields.exists(field => field.equals("*") || field.startsWith("categories"))

        if(includeTaxonomy) {
          val productListFuture = productList map { product =>
            categoryService.getProductTaxonomy(product.getId, site, getCategoryFields(fields)) map { categories =>
              product.categories = Option(categories)
              product
            }
          }

          //Combine all futures in a single one (wait until they all finish)
          Future.sequence(productListFuture).map(productResponse => {
            withCacheHeaders(Ok(Json.obj(
              "metadata" -> Json.obj(
                "found" -> productResponse.size,
                "time" -> (System.currentTimeMillis() - startTime)),
              "products" -> productResponse)), id)
          })
        }
        else {
          Future(withCacheHeaders(Ok(Json.obj(
            "metadata" -> Json.obj(
              "found" -> productList.size,
              "time" -> (System.currentTimeMillis() - startTime)),
            "products" -> Json.toJson(productList))), id))
        }
      } else {
        Logger.debug(s"Products with ids [$id] not found")
        Future(NotFound(Json.obj(
          "messages" -> s"Cannot find products with ids [$id]"
        )))
      }
    }

    withErrorHandling(future, s"Cannot retrieve products with ids [$id]")
  }

  /**
   * @param fields the product field list
   * @return the field list for product categories
   */
  private def getCategoryFields(fields: Seq[String]) = fields withFilter {
    field =>
      field.startsWith("categories.") || field.equals("*")
  } map {
    field =>
      field.replaceFirst("categories\\.", "")
  }

  /**
   * Helper method to process search results
   * @param groupSummary returned by Solr
   * @return an array for products containing its summary
   */
  private def processGroupSummary(groupSummary: NamedList[Object])(implicit context: Context) : JsObject = {
    val groups = new ArrayBuffer[(String, JsValue)]()

    if(groupSummary != null) {
      val productSummaries = groupSummary.get("productId").asInstanceOf[NamedList[Object]]

      if(productSummaries != null) {
       productSummaries map { productSummary =>
         val parameterSummaries = productSummary.getValue.asInstanceOf[NamedList[Object]]
         val productSeq = ArrayBuffer[(String,JsValue)]()

         parameterSummaries map { parameterSummary =>
           val statSummaries = parameterSummary.getValue.asInstanceOf[NamedList[Object]]
           val parameterSeq = ArrayBuffer[(String,JsString)]()

           statSummaries map { statSummary =>
             parameterSeq += ((statSummary.getKey, new JsString(statSummary.getValue.toString)))
           }

           //Remove country from parameters
           var parameter = parameterSummary.getKey
           if(parameter.endsWith(context.lang.country)) {
             parameter = parameter.substring(0, parameter.length - context.lang.country.length)
           }

           productSeq += ((parameter, new JsObject(parameterSeq)))
         }

         groups += ((productSummary.getKey, new JsObject(productSeq)))
       }
      }
    }

    new JsObject(groups)
  }


  /**
   * Helper method to process search results
   * @param q is the query
   * @param response the Solr response
   * @return a tuple with the total number of products found and the list of product documents in the response
   */
  private def processSearchResults[R](q: String, response: QueryResponse)(implicit context: Context, req: Request[R]) : Future[(Int, Iterable[Product], NamedList[Object])] = {
    val groupResponse = response.getGroupResponse
    val groupSummary = response.getResponse.get("groups_summary").asInstanceOf[NamedList[Object]]

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
            val storage = withNamespace(storageFactory)
            storage.findProducts(products, context.lang.country, fieldList(allowStar = true), minimumFields = true).map( products => {
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
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def search(
      version: Int,
      @ApiParam(value = "Search term", required = true)
      @QueryParam("q")
      q: String,
      @ApiParam(value = "Site to search", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean) = ContextAction.async { implicit context =>  implicit request =>

    val startTime = System.currentTimeMillis()
    val query = new ProductSearchQuery(q, site)
      .withFilterQueries()
      .withFaceting()
      .withPagination()
      .withSorting()
      .withGrouping()

    Logger.debug("Searching for " + q)

    val future: Future[SimpleResult] = solrServer.query(query).flatMap( response => {
      val redirect = response.getResponse.get("redirect_url")
      if (redirect != null && StringUtils.isNotBlank(redirect.toString)) {
         Future.successful(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "redirectUrl" -> redirect.toString,
                  "time" -> (System.currentTimeMillis() - startTime)
         ))))
      } else if (query.getRows > 0) {
        processSearchResults(q, response).map { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              val facetHandler = buildFacetHandler(response, query, query.filterQueries)
              withCacheHeaders(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "productSummary" -> processGroupSummary(groupSummary),
                  "time" -> (System.currentTimeMillis() - startTime),
                  "facets" -> facetHandler.getFacets,
                  "breadCrumbs" -> facetHandler.getBreadCrumbs),
                "products" -> Json.toJson(
                  products map (Json.toJson(_))
                ))), products map (_.getId))
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
            Ok(Json.obj(
              "metadata" -> Json.obj(
                "found" -> 0,
                "time" -> (System.currentTimeMillis() - startTime)),
              "message" -> "No products found"))
          }
        }
      } else {
        Future.successful(Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> response.getResults.getNumFound,
            "time" -> (System.currentTimeMillis() - startTime)))))
      }
    })

    withErrorHandling(future, s"Cannot search for [$q]")
  }

  @ApiOperation(value = "Browses brand products ", notes = "Returns products for a given brand", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def browseBrand(
      version: Int,
      @ApiParam(value = "Site to browse", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(value = "Brand to browse", required = true)
      @PathParam("brandId")
      brandId: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean) = ContextAction.async { implicit context => implicit request =>
    Logger.debug(s"Browsing brand $brandId")
    withErrorHandling(doBrowse(version, null, site, brandId, isOutlet = false), s"Cannot browse brand [$brandId]")
  }

  @ApiOperation(value = "Browses brand's category products ", notes = "Returns products for a given brand category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display outlet results", required = false)
      @QueryParam("outlet")
      outlet: Boolean) = ContextAction.async { implicit context => implicit request =>
    Logger.debug(s"Browsing category $categoryId for brand $brandId")
    withErrorHandling(doBrowse(version, categoryId, site, brandId, isOutlet = false), s"Cannot browse brand category [$brandId - $categoryId]")
  }

  @ApiOperation(value = "Browses category products ", notes = "Returns products for a given category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
      outlet: Boolean) = ContextAction.async { implicit context => implicit request =>
    Logger.debug(s"Browsing $categoryId")
    withErrorHandling(doBrowse(version, categoryId, site, null, outlet), s"Cannot browse category [$categoryId]")
  }

  private def doBrowse(version: Int, categoryId: String, site: String, brandId: String, isOutlet: Boolean)(implicit context: Context, request: Request[AnyContent]) = {
    val startTime = System.currentTimeMillis()
    val query = new ProductBrowseQuery(site)
      .withFilterQueries()
      .withFaceting()
      .withPagination()
      .withSorting()
      .withGrouping()

    val storage = withNamespace(storageFactory)
    storage.findCategory(categoryId, Seq("hierarchyTokens", "ruleFilters", "isRuleBased")).flatMap { category =>
      
      var isRulePage = false
      if (category != null && category.isRuleBased.get) {
        //set the rules expression filter query for rule based categories
        val lang = context.lang
        val localeKey = s"${lang.language}_${lang.country}"
        val ruleFilters = category.ruleFilters.getOrElse(Seq.empty[String]).filter(rule => {
            rule.startsWith(localeKey)
        })

        query.withRules(ruleFilters)
        isRulePage = true
      } else {
        //otherwise handle a regular category or a brand category
        if (StringUtils.isNotBlank(categoryId)) {
          query.withAncestorCategory(categoryId)
        }
        if (StringUtils.isNotBlank(brandId)) {
          query.withBrand(brandId)
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
        if (query.getRows > 0) {
          val groupResponse = response.getGroupResponse
          val groupSummary = response.getResponse.get("groups_summary").asInstanceOf[NamedList[Object]]

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
                  val storage = withNamespace(storageFactory)
                  storage.findProducts(products, context.lang.country, fieldList(allowStar = true), minimumFields = true).map(products => {
                    val facetHandler = buildFacetHandler(response, query, query.filterQueries)
                    withCacheHeaders(Ok(Json.obj(
                      "metadata" -> Json.obj(
                        "found" -> command.getNGroups.intValue(),
                        "productSummary" -> processGroupSummary(groupSummary),
                        "time" -> (System.currentTimeMillis() - startTime),
                        "facets" -> facetHandler.getFacets,
                        "breadCrumbs" -> facetHandler.getBreadCrumbs),
                      "products" -> Json.toJson(
                        products map (Json.toJson(_))
                      ))
                    ), products map (_.getId))
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
                Future.successful(Ok(Json.obj(
                  "metadata" -> Json.obj(
                    "found" -> 0,
                    "time" -> (System.currentTimeMillis() - startTime)),
                  "message" -> "No products found")))
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
        else {
          Future.successful(Ok(Json.obj(
            "metadata" -> Json.obj(
              "found" -> response.getResults.getNumFound,
              "time" -> (System.currentTimeMillis() - startTime)))))
        }
      }
    }
  }

  private def buildFacetHandler[R](response: QueryResponse, query: SolrQuery, filterQueries: Array[FilterQuery])(implicit context: Context, req: Request[R]) : FacetHandler = {
    var facetData = Seq.empty[NamedList[AnyRef]]

    if (response.getResponse != null && response.getResponse.get("rule_facets") != null) {
      facetData = response.getResponse.get("rule_facets").asInstanceOf[util.ArrayList[NamedList[AnyRef]]]
    }
    new FacetHandler(query, response, filterQueries, facetData, withNamespace(storageFactory))
  }

  def bulkCreateOrUpdate(version: Int) = ContextAction.async(parse.json(maxLength = 1024 * 2000)) { implicit context => implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>
      val products = productList.products
      if (products.size > MaxProductIndexBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxProductIndexBatchSize")))
      } else {
        try {
          val (_, skuDocs) = productList.toDocuments(categoryService)
          def countProduct(product: Product) = if (product.isOem.getOrElse(false)) 0 else 1
          val productCount = productList.products.foldLeft(0)((total, product) => total + countProduct(product))
          if (productCount > 0 && skuDocs.isEmpty) {
              Future.successful(BadRequest(Json.obj(
              "message" -> "Cannot store a product without skus. Check that the required fields of the products are set")))
          } else {
            val storage = withNamespace(storageFactory)
            val productFuture = storage.saveProduct(products:_*)
            var futureList: List[Future[AnyRef]] = List(productFuture)

            if (!skuDocs.isEmpty) {
              val productUpdate = new ProductUpdate
              productUpdate.add(skuDocs)
              val searchFuture: Future[UpdateResponse] = productUpdate.process(solrServer)
              val suggestionFuture = IndexableElement.addToIndex(products)
              futureList = List(productFuture, searchFuture, suggestionFuture)
            }

            val future: Future[SimpleResult] = Future.sequence(futureList) map { result =>
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
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "preview", value = "Deletes products in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All products with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long) = ContextAction.async { implicit context => implicit request =>

    val update = new ProductUpdate()
    update.deleteByQuery("-indexStamp:" + feedTimestamp)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })


    withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
  }

  @ApiOperation(value = "Deletes products", notes = "Deletes the given product", httpMethod = "DELETE")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "feedTimestamp", value = "The feed timestamp. If provided, only skus with a different timestamp are deleted", required = false, dataType = "long", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Deletes products in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def deleteById(
      version: Int = 1,
      @ApiParam(value = "A product id", required = false)
      @PathParam("id")
      id: String) = ContextAction.async { implicit context => implicit request =>
    val update = new ProductUpdate()
    val feedTimestamp = request.getQueryString("feedTimestamp")

    if (feedTimestamp.isDefined) {
      update.deleteByQuery(s"productId:$id AND -indexStamp: ${feedTimestamp.get}")
    } else {
      update.deleteByQuery(s"productId:$id")
    }

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      NoContent
    })

    withErrorHandling(future, s"Cannot delete product [$id  ]")
  }

  @ApiOperation(value = "Suggests products", notes = "Returns product suggestions for given partial product title", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "id,title", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial product title is too short")))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial product title", required = true)
      @QueryParam("q")
      q: String) = ContextAction.async { implicit context => implicit request =>
    val startTime = System.currentTimeMillis()
    var query = new SolrQuery(q)
    query.set("group", true)
      .set("group.ngroups", true)
      .set("group.field", "productId")
      .set("group.facet", false)

    val fields = request.getQueryString("fields").getOrElse("")
    if(fields.contains("skus")) {
      query.set("group.limit", 50)
    } else {
      query.set("group.limit", 1)
    }

    // @todo revisit this query
    val solrQuery = withPagination(withFields(query, request.getQueryString("fields"))).setParam("collection", searchCollection.name(lang))
    solrQuery.setRequestHandler("suggest")
    val future: Future[SimpleResult] = solrServer.query(solrQuery).flatMap( response => {
      if (query.getRows > 0) {
        processSearchResults(q, response).map { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> (System.currentTimeMillis() - startTime)),
              "suggestions" -> Json.toJson(
                products map (Json.toJson(_)))))
            } else {
              Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> (System.currentTimeMillis() - startTime)),
                "suggestions" -> Json.arr()
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
            "found" -> response.getResults.getNumFound,
            "time" -> (System.currentTimeMillis() - startTime)))))
      }
    })
    withErrorHandling(future, s"Cannot search for [$q]")
  }

}
