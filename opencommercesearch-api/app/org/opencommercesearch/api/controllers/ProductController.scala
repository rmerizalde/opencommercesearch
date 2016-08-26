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

import java.{util => jutil}
import javax.ws.rs.{PathParam, QueryParam}

import com.wordnik.swagger.annotations._
import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.response.{GroupCommand, QueryResponse, SpellCheckResponse, UpdateResponse}
import org.apache.solr.client.solrj.util.ClientUtils
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.Collection._
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api._
import org.opencommercesearch.api.common.{FacetHandler, FilterQuery}
import org.opencommercesearch.api.i18n.Lang
import org.opencommercesearch.api.models._
import org.opencommercesearch.api.models.debug.DebugInfo
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.api.util.Timer
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.suggester.IndexableElement
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.core.commands.LastError

import scala.collection.JavaConversions._
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

@Api(value = "products", basePath = "/api-docs/products", description = "Product API endpoints")
object ProductController extends BaseController {
  val categoryService = new CategoryService(solrServer, storageFactory)

  private val OrOperator = "OR"

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

    val startTime = Some(System.currentTimeMillis())
    val storage = withNamespace(storageFactory)
    var productFuture: Future[Iterable[Product]] = null
    val productIds = StringUtils.split(id, ",").take(MaxProductsLimit).map(i => (i, null))

    val fields = fieldList(allowStar = true)

    if (site != null) {
      productFuture = storage.findProducts(productIds, site, context.lang.country, fields, minimumFields = false, context.isPreview )
    } else {
      productFuture = storage.findProducts(productIds, context.lang.country, fields, minimumFields = false, context.isPreview )
    }

    val future = productFuture flatMap { products =>
      def createResponse() = {
        val metadataFields = StringUtils.split(request.getQueryString("metadata").getOrElse(""), ',')

        if (products.size > 0) {
          val summary = if (metadataFields.isEmpty || metadataFields.contains("productSummary")) createProductSummary(products) else None

          withCacheHeaders(buildProductResponse(metadataFields = metadataFields, found = Some(products.size),
            productSummary = summary, startTime = startTime,
            products = Some(products map (Json.toJson(_)))), id)
        } else {
          withCacheHeaders(buildProductResponse(metadataFields = metadataFields, found = Some(0), startTime = startTime,
            message = Some("No products found")), id)
        }
      }

      if (products != null) {
       //Check if we should include the category taxonomy or not
        val includeTaxonomy = fields.isEmpty || fields.exists(field => field.equals("*") || field.startsWith("categories"))

        if (includeTaxonomy) {
          val productListFuture = products map { product =>
            val categoryIds = product.categories.getOrElse(Seq.empty).map(category => category.getId).toSet
            categoryService.getProductTaxonomy(product.getId, site, getCategoryFields(fields), categoryIds) map { categories =>
              product.categories = Option(categories)
              product
            }
          }

          //Combine all futures in a single one (wait until they all finish)
          Future sequence productListFuture  map { products => createResponse() }
        } else {
          Future(createResponse())
        }
      } else {
        Logger.debug(s"Products with ids [$id] not found")
        Future(withCorsHeaders(NotFound(Json.obj(
          "messages" -> s"Cannot find products with ids [$id]"
        ))))
      }
    }

    withErrorHandling(future, s"Cannot retrieve products with ids [$id]", request)
  }

  /**
   * Helper method that constructs a product response
   *
   * @return a simple result containing a proper product response body.
   */
  private def buildProductResponse(
                                   metadataFields: Array[String],
                                   found: Option[Long] = None,
                                   productSummary: Option[JsObject] = None,
                                   startTime: Option[Long] = None,
                                   message: Option[String] = None,
                                   products: Option[Iterable[JsValue]] = None) : Result = {

    val metadataValues = Seq[Option[(String, JsValueWrapper)]](
      found withFilter { v => metadataFields.isEmpty || metadataFields.contains("found") } map {v => "found" -> v},
      startTime map {v => "time" -> (System.currentTimeMillis() - v)},
      productSummary map {v => "productSummary" -> v}
    )

    val responseValues = Seq[Option[(String, JsValueWrapper)]](
      Some("metadata" -> Json.obj(metadataValues.flatten:_*)),
      message map {v => "message" -> v},
      products map {v => "products" -> products}
    )

    val json = Json.obj(responseValues.flatten:_*)
    if (found.getOrElse(0l) > 0l) withCorsHeaders(Ok(json)) else withCorsHeaders(NotFound(json))
  }

  /**
   * Helper method to calculate a summary for the given list of products
   */
  private def createProductSummary(products: Iterable[Product]): Option[JsObject] = {
    def colorTransformer(families: JsValue) = families match {
      case v: JsUndefined => __.json.pick
      case _ =>
        (__ \ 'color).json.update(__.read[JsObject].map { o => o ++ Json.obj("families" -> families)}) andThen
        (__ \ 'colorFamily).json.prune
    }

    val summaries: Iterable[(String, JsValue)] = (products withFilter { product =>
      product.skus.getOrElse(Seq[Sku]()).size > 0
    } map { product =>
      ProductSummary.summarize(product) map { summary =>
        val json = Json.toJson(summary)
        val colorFamilies = json \ "colorFamily" \ "distinct"
        val transformedJson = json.transform(colorTransformer(colorFamilies)) match {
          case JsSuccess(value, _) => value
          case JsError(errors) =>
            // setting to debug, transformer will fail when the color field is not selecting. Prevent log polution
            Logger.debug(s"Cannot transform summary: $errors")
            // and return original json instead
            json
        }
        (product.id.get, transformedJson)
      }
    }).flatten

    if (summaries.size > 0) Some(new JsObject(summaries.toSeq)) else None
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
      field.replaceFirst("categories\\.", "") match {
        case "id" => "_id"
        case f => f
      }
  }

  /**
   * Helper method to process search results
   * @param groupSummary returned by Solr
   * @return an array for products containing its summary
   */
  private def processGroupSummary(site: String, groupSummary: NamedList[Object])(implicit context: Context) : Option[JsObject] = {
    if (groupSummary != null && groupSummary.size() > 0) {
      def namedList(value: AnyRef) = value.asInstanceOf[NamedList[AnyRef]]

      // todo: this is a workaround and will be deleted in a future version
      def patchColorSummaries(summaries: NamedList[AnyRef]) {
        def patchDistinct(summary: NamedList[AnyRef]) = if (summary.get("distinct") != null) {
          summary.add("families", summary.remove("distinct"))
        } else {
          summary
        }
        def patch(colorSummary: NamedList[AnyRef], colorFamilySummary: NamedList[AnyRef]) = if (colorFamilySummary != null) {
          val families = colorFamilySummary.get("families")
          if (families != null) {
            if (colorSummary != null) {
              colorSummary.add("families", families)
            }
            summaries.remove("colorFamily")
          }
        }

        patchDistinct(namedList(summaries.get("colorFamily")))
        patch(namedList(summaries.get("color")), namedList(summaries.get("colorFamily")))
      }

      // @todo: refactor schema to have all names with consistent case
      def patchParamName(name: String) = if (name == "onsale") "onSale" else name

      implicit val implicitAnyRefWrites = new Writes[Any] {
        def writes(any: Any): JsValue = any match {
          case f: Float => JsNumber(BigDecimal(any.toString))
          case i: Int => JsNumber(any.asInstanceOf[Int])
          case m: jutil.Map[_, Int] => Json.toJson(m.map(kv => (kv._1.toString, kv._2)).toMap) // todo: dirty hack to serialize the buckets map. Type erasure doesn't play well with pattern matching, figure out workaround
          case a: jutil.ArrayList[_] => JsArray(a.map { e => Json.toJson(e.toString)}) // todo: support list types other than String
          case _ => JsString(any.toString)
        }
      }

      val groups = new ArrayBuffer[(String, JsValue)]()

      if (groupSummary != null) {
        val productSummaries = namedList(groupSummary.get("productId"))

        if (productSummaries != null) {
          productSummaries map { productSummary =>
            val parameterSummaries = namedList(productSummary.getValue)
            val productSeq = ArrayBuffer[(String, JsValue)]()

            patchColorSummaries(parameterSummaries)
            parameterSummaries map { parameterSummary =>
              val statSummaries = parameterSummary.getValue.asInstanceOf[NamedList[AnyRef]]
              val parameterSeq = ArrayBuffer[(String, JsValue)]()

              statSummaries map { statSummary =>
                parameterSeq += ((statSummary.getKey, Json.toJson(statSummary.getValue)))
              }

              //Remove country from parameters
              var parameter = parameterSummary.getKey
              if (parameter.endsWith(site)) {
                parameter = parameter.substring(0, parameter.length - site.length)
              }
              if (parameter.endsWith(context.lang.country)) {
                parameter = parameter.substring(0, parameter.length - context.lang.country.length)
              }

              productSeq += ((patchParamName(parameter), new JsObject(parameterSeq)))
            }


            groups += ((productSummary.getKey, new JsObject(productSeq)))
          }
        }
      }
      Some(new JsObject(groups))
    } else {
      None
    }
  }

  /**
   * Helper method to process search results
   * @param response the Solr response
   * @param errorMessage is the error message
   * @return a tuple with the total number of products found and the list of product documents in the response and the group summary
   */
  private def processSearchResults[R](site: String, response: QueryResponse, errorMessage: String)(implicit context: Context, req: Request[R]) : Future[(Int, Iterable[Product], NamedList[Object])] = {
    val groupResponse = response.getGroupResponse

    if (groupResponse != null) {
      val commands = groupResponse.getValues

      if (commands.size > 0) {
        val command = groupResponse.getValues.get(0)
        val productsIds = new jutil.ArrayList[(String, String)]
        if ("productId".equals(command.getName)) {
          if (hasResults(command)) {
            for (group <- JIterableWrapper(command.getValues)) {
              val documentList = group.getResult
              val product  = documentList.get(0)
              product.setField("productId", group.getGroupValue)
              productsIds.add((group.getGroupValue, product.getFieldValue("id").asInstanceOf[String]))
            }

            val storage = withNamespace(storageFactory)
            val fields = fieldList(allowStar = true)
            storage.findProducts(productsIds, site, context.lang.country, fields, minimumFields = true, context.isPreview ).flatMap { products =>
              val groupSummary = response.getResponse.get("groups_summary").asInstanceOf[NamedList[Object]]

              val includeTaxonomy = site != null && (fields.isEmpty || fields.exists(field => field.equals("*") || field.startsWith("categories")))

              if (includeTaxonomy) {
                val t = System.currentTimeMillis()
                val productListFuture = products map { product =>
                  val categoryIds = product.categories.getOrElse(Seq.empty).map(category => category.getId).toSet
                  categoryService.getProductTaxonomy(product.getId, site, getCategoryFields(fields), categoryIds) map { categories =>
                    product.categories = Option(categories)
                    product
                  }
                }

                Future sequence productListFuture map { products =>
                  (resultCount(command), products, groupSummary)
                }
              } else {
                Future((resultCount(command), products, groupSummary))
              }
            }
          } else {
            Future.successful((0, null, null))
          }
        } else {
          Logger.debug(errorMessage)
          Future.successful((0, null, null))
        }
      } else {
        Logger.debug(errorMessage)
        Future.successful((0, null, null))
      }
    } else {
      if (response.getResults != null) {
        val storage = withNamespace(storageFactory)
        val productsIds: Seq[(String, String)] = response.getResults map { doc =>
          val productId = doc.get("productId").asInstanceOf[String]
          val expandedDocList = response.getExpandedResults.get(productId)
          val skuId = expandedDocList.get(0).get("id").asInstanceOf[String]
          (productId, skuId)
        }

        storage.findProducts(productsIds, context.lang.country, fieldList(allowStar = true), minimumFields = true, context.isPreview).map { products =>
          val groupSummary = response.getResponse.get("groups_summary").asInstanceOf[NamedList[Object]]

          (response.getResults.getNumFound.toInt, products, groupSummary)
        }
      } else {
        Future.successful((0, null, null))
      }
    }
  }

  @ApiOperation(value = "Searches products", notes = "Returns products for a given query", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query"),
    new ApiImplicitParam(name = "debug", value = "Add debugging metadata", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
      outlet: Boolean,
      @ApiParam(defaultValue="auto", allowableValues="auto,yes,no", value = "Whether or not query spell checking should be done. If set to auto and the original " +
        "query returns zero results, the search is retried with the spell check corrected terms. If set to yes, only correctedTerms and suggested terms is returned, no search is retried.", required = false)
      @QueryParam("spellCheck")
      spellCheckParam: String,
      @QueryParam("redirects")
      redirects: Boolean) = ContextAction.async { implicit context =>  implicit request =>

    val startTime = System.currentTimeMillis()
    val spellCheckMode = spellCheckParam.toLowerCase
    val query = new ProductSearchQuery(q, site)
      .withFilterQueries()
      .withFaceting()
      .withPagination()
      .withSorting()
      .withGrouping()
      .withOutlet()
      .withRedirects()
      .withCustomParams()
      .withSpellCheck(spellCheckMode != "no")
      .withDebugInfo()

    Logger.debug(s"Searching for '$q', spell checking set to '$spellCheckMode'")

    val future = doSearch(query, spellCheckMode, startTime) flatMap { case (spellCheck, partialMatch, response) =>
      val redirect = response.getResponse.get("redirect_url")
      if (redirect != null && StringUtils.isNotBlank(redirect.toString)) {
        Future.successful(buildSearchResponse(query = query, startTime = Some(startTime), redirectUrl = Some(redirect.toString)))
      }
      else if(query.getRows > 0) {
        val unexpectedErrorMessage = s"Unexpected response found for query '$q'"
        processSearchResults(site, response, unexpectedErrorMessage).flatMap { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              val facetHandler = buildFacetHandler(response, query, query.filterQueries)
              facetHandler.getFacets map { facets =>
                withCacheHeaders(buildSearchResponse(
                  query = query,
                  breadCrumbs = Some(facetHandler.getBreadCrumbs),
                  facets = Some(facets),
                  found = Some(found),
                  productSummary = processGroupSummary(site, groupSummary),
                  spellCheck = Option(spellCheck),
                  partialMatch = Option(partialMatch),
                  startTime = Some(startTime),
                  products = Some(products map (Json.toJson(_))),
                  debugInfo = buildDebugInfo(response)), products map (_.getId))
              }
            } else {
              Future.successful(buildSearchResponse(
                query = query,
                found = Some(found),
                productSummary = processGroupSummary(site, groupSummary),
                spellCheck = Option(spellCheck),
                partialMatch = Option(partialMatch),
                startTime = Some(startTime),
                message = Some("No products found")))
            }
          } else {
            Logger.debug(s"No results found for query '${query.getQuery}', returning spell check suggestions if any.")
            Future.successful(buildSearchResponse(query = query, found = Some(0), spellCheck =  Option(spellCheck), startTime = Some(startTime), message = Some("No products found")))
          }
        }
      } else {
        Future.successful(buildSearchResponse(query = query, found = Some(response.getResults.getNumFound), spellCheck = Option(spellCheck), startTime = Some(startTime)))
      }
    }

    withErrorHandling(future, s"Cannot search for [${query.getQuery}]", request)
  }

  /**
   * Does the actual search.
   * @param query Original product query
   * @param spellCheck Spell check setting (auto, yes, no)
   * @param startTime Time when this search request started.
   * @return A spellcheck response (can be null) and a query response.
   */
  def doSearch(query: ProductQuery, spellCheck: String, startTime: Long) : Future[(JsObject, Boolean, QueryResponse)] = {
    val q = query.getQuery

    retry(solrServer.query(query)) flatMap { response =>
      def isRedirect = {
        val redirect = response.getResponse.get("redirect_url")
        redirect != null && StringUtils.isNotBlank(redirect.toString)
      }

      if (query.getRows > 0 && StringUtils.isNotBlank(q) && !isRedirect && !hasResults(response)) {
        if (spellCheck != "no") {
          //Do spell checking
          if (spellCheck == "auto") {
            handleSpellCheck(query, response) flatMap { case (spellCheckResponse, partialMatch, tentativeResponse) =>
              if (spellCheckResponse != null && hasResults(tentativeResponse)) {
                Future(spellCheckResponse, partialMatch, tentativeResponse)
              } else {
                handlePartialMatching(query, response) map {
                  // @todo: checkout this variable shadowing
                  case (partialMatch, tentativeResponse) => (spellCheckResponse, partialMatch, tentativeResponse)
                } 
              }
            }
          }
          else {
            handlePartialMatching(query, response) flatMap { case (partialMatch, tentativeResponse) =>
              //Return spell check suggestions is any, let the client handle it.
              Future((spellCheckToJson(response.getSpellCheckResponse), partialMatch, tentativeResponse))
            }
          }
        } 
        else {
          handlePartialMatching(query, response) map {
            case (partialMatch, tentativeResponse) => (null, partialMatch, tentativeResponse)
          } 
        }
      }
      else {
        Future((null, false, response))
      }
    }
  }

  /**
   * Converts from Solr spell check response to Json (called when spellcheck=true)
   * @param spellCheckResponse Solr spell check response
   * @return A Json object in the appropiate format.
   */
  private def spellCheckToJson(spellCheckResponse: SpellCheckResponse) : JsObject = {
    if(spellCheckResponse != null) {
      Json.obj(
        "terms" -> spellCheckResponse.getSuggestions.map({suggestion =>
          Json.obj(
            "term" -> suggestion.getToken,
            "found" -> suggestion.getNumFound,
            "startOffset" -> suggestion.getStartOffset,
            "endOffset" -> suggestion.getEndOffset,
            "suggestions" -> suggestion.getAlternatives.toIterable
          )}),
        "collation" -> spellCheckResponse.getCollatedResult
      )
    }
    else {
      null
    }
  }

  /**
   * Retries the search trying to match any terms (by default, Solr results must match ALL terms in the query).
   * @param query A query that returned zero results.
   * @param response The response of the given query
   * @return The products from doing an OR query in solr for each individual term
   */
  private def handlePartialMatching(query: ProductQuery, response: QueryResponse): Future[(Boolean, QueryResponse)] = {
    query.setParam("q.op", OrOperator)
    query.setParam("mm", SearchMinimumMatch)

    Logger.debug(s"Searching using partial matching '${query.getQuery}'")
    retry(solrServer.query(query)) map { tentativeResponse =>
      if (hasResults(tentativeResponse)) {
        (true, tentativeResponse)
      } else {
        (false, response)
      }
    }
  }

  /**
   * Retries the search with the best spell check suggestion (if any). If that doesn't give results, then try again matching any term (by default, Solr results must match ALL terms in
   * the query).
   * @param query A query that returned zero results.
   * @param response The response of the given query
   * @return The best query response. If spell checking didn't return anything useful, it returns the original query response.
   */
  private def handleSpellCheck(query: ProductQuery, response: QueryResponse) : Future[(JsObject, Boolean, QueryResponse)] = {
    val spellCheckResponse = response.getSpellCheckResponse

    if (spellCheckResponse != null && StringUtils.isNotBlank(spellCheckResponse.getCollatedResult)) {
      //Check if we have any spelling suggestion
      val tentativeQuery = spellCheckResponse.getCollatedResult

      query.setQuery(tentativeQuery)
      Logger.debug(s"Searching spell check suggestion '$tentativeQuery'")
      retry(solrServer.query(query)) flatMap { tentativeResponse =>
        if (hasResults(tentativeResponse)) {
          Future(Json.obj(
            "correctedTerms" -> tentativeQuery), false, tentativeResponse)
        } else {
          Future(null, false, response)
        }
      }
    } else {
      Future(null, false, response)
    }
  }

  /**
   * Helper method that constructs a search response
   *
   * @return a simple result containing a proper search response body.
   */
  private def buildSearchResponse(
                                   query: ProductQuery,
                                   breadCrumbs: Option[Seq[BreadCrumb]] = None,
                                   facets: Option[Seq[Facet]] = None,
                                   found: Option[Long] = None,
                                   productSummary: Option[JsObject] = None,
                                   redirectUrl: Option[String] = None,
                                   spellCheck: Option[JsObject] = None,
                                   partialMatch: Option[Boolean] = None,
                                   startTime: Option[Long] = None,
                                   message: Option[String] = None,
                                   products: Option[Iterable[JsValue]] = None,
                                   debugInfo: Option[DebugInfo] = None) : Result = {

    val metadataValues = Seq[Option[(String, JsValueWrapper)]](
      redirectUrl map {v => "redirectUrl" ->  v},
      found withFilter { v => query.groupTotalCount } map {v => "found" -> v},
      startTime map {v => "time" -> (System.currentTimeMillis() - v)},
      productSummary map {v => "productSummary" -> v},
      facets withFilter { v => v.size > 0 } map {v => "facets" -> v},
      breadCrumbs withFilter { v => v.size > 0 } map {v => "breadCrumbs" -> v},
      spellCheck map {v => "spellCheck" -> v},
      partialMatch map {v => "partialMatch" -> v},
      debugInfo map { v => "debug" -> v}
    )

    val responseValues = Seq[Option[(String, JsValueWrapper)]](
      Some("metadata" -> Json.obj(metadataValues.flatten:_*)),
      message map {v => "message" -> v},
      products map {v => "products" -> products}
    )

    withCorsHeaders(Ok(Json.obj(responseValues.flatten:_*)))
  }

  /**
   * Helper method that tells if a Solr query response has results or not.
   * @param response The Solr query response.
   * @return True, if the response contains results,false otherwise.
   */
  private def hasResults(response: QueryResponse) : Boolean = {
    if(response.getGroupResponse != null) {
      val groupResponse = response.getGroupResponse

      groupResponse.getValues.collectFirst({
        case command: GroupCommand if hasResults(command) => true
      }).getOrElse(false)
    }
    else {
      response.getResults != null && response.getResults.getNumFound > 0
    }
  }

  def hasResults(command: GroupCommand) = if (command.getNGroups == null) {
    command.getMatches > 0
  } else {
    command.getNGroups > 0
  }

  def resultCount(command: GroupCommand) = if (command.getNGroups != null) {
    command.getNGroups.intValue()
  } else {
    command.getMatches
  }

  @ApiOperation(value = "Browses brand products ", notes = "Returns products for a given brand", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query"),
    new ApiImplicitParam(name = "debug", value = "Add debugging metadata", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
    withErrorHandling(doBrowse(version, null, site, brandId, isOutlet = false), s"Cannot browse brand [$brandId]", request)
  }

  @ApiOperation(value = "Browses brand's category products ", notes = "Returns products for a given brand category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query"),
    new ApiImplicitParam(name = "debug", value = "Add debugging metadata", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
    withErrorHandling(doBrowse(version, categoryId, site, brandId, isOutlet = false), s"Cannot browse brand category [$brandId - $categoryId]", request)
  }

  @ApiOperation(value = "Browses category products ", notes = "Returns products for a given category", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "sort", value = "Comma delimited list of sort clauses to apply on the product list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query"),
    new ApiImplicitParam(name = "debug", value = "Add debugging metadata", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
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
    withErrorHandling(doBrowse(version, categoryId, site, null, outlet), s"Cannot browse category [$categoryId]", request)
  }

  
  private def doBrowse(version: Int, categoryId: String, site: String, brandId: String, isOutlet: Boolean)(implicit context: Context, request: Request[AnyContent]) = {
    val startTime = System.currentTimeMillis()
    val query = new ProductBrowseQuery(site)
      .withFilterQueries()
      .withFaceting()
      .withPagination()
      .withSorting()
      .withGrouping()
      .withCustomParams()
      .withDebugInfo()

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
        //filter by outlet/onsale only for category pages. Rule categories should skip this filter
        query.withOutlet()

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
            if (token.substring(token.indexOf(".") + 1).startsWith(site)) {
              val escapedCategoryFilter = ClientUtils.escapeQueryChars(token)
              query.add("categoryFilter", escapedCategoryFilter)
              if (!isRulePage) {
                //if we are in a rule based category, solr won't have the category indexed, so we need to avoid this request
                query.addFilterQuery("category:" + escapedCategoryFilter)
              }
            }
          }
        }
      }

      solrServer.query(query).flatMap { response =>
        if (query.getRows > 0) {
          val unexpectedErrorMessage = s"Unexpected response found for category '$categoryId' (brand=$brandId isOutlet:$isOutlet)"
          processSearchResults(site, response, unexpectedErrorMessage).flatMap { case (found, products, groupSummary) =>
            if (products != null) {
              if (found > 0) {
                val facetHandler = buildFacetHandler(response, query, query.filterQueries)
                facetHandler.getFacets map { facets =>
                  withCacheHeaders(buildSearchResponse(
                    query = query,
                    breadCrumbs = Some(facetHandler.getBreadCrumbs),
                    facets = Some(facets),
                    found = Some(found),
                    productSummary = processGroupSummary(site, groupSummary),
                    startTime = Some(startTime),
                    products = Some(products map (Json.toJson(_))),
                    debugInfo = buildDebugInfo(response)), products map (_.getId))
                }
              } else {
                Future.successful(buildSearchResponse(
                  query = query,
                  found = Some(found),
                  productSummary = processGroupSummary(site, groupSummary),
                  startTime = Some(startTime),
                  message = Some("No products found")))
              }
            } else {
              Logger.debug(s"No results found for category '$categoryId' (brand=$brandId isOutlet:$isOutlet)")
              Future.successful(buildSearchResponse(query = query, found = Some(0), startTime = Some(startTime), message = Some("No products found")))
            }
          }
        } else {
          Future.successful(buildSearchResponse(query = query, found = Some(response.getResults.getNumFound), startTime = Some(startTime)))
        }
      }
    }
  }

  private def buildFacetHandler[R](response: QueryResponse, query: SolrQuery, filterQueries: Array[FilterQuery])(implicit context: Context, req: Request[R]) : FacetHandler = {
    var facetData = Seq.empty[NamedList[AnyRef]]

    if (response.getResponse != null && response.getResponse.get("rule_facets") != null) {
      facetData = response.getResponse.get("rule_facets").asInstanceOf[jutil.ArrayList[NamedList[AnyRef]]]
    }
    new FacetHandler(query, response, filterQueries, facetData, withNamespace(storageFactory))
  }

  /**
   * Utility method to create a DebugInfo model object from the solr response. This object includes the solr score debug section
   * as well as the rule debug one
   */
  private def buildDebugInfo[R](response: QueryResponse) (implicit context: Context, req: Request[R]) : (Option[DebugInfo]) = {
    val debugInfo = new DebugInfo()
    var generated = false

    if (response.getResponse != null && response.getResponse.get("rule_debug") != null) {
      generated = true
      debugInfo.processRulesResponse(response.getResponse.get("rule_debug").asInstanceOf[jutil.Map[String, AnyRef]])
    }
    if (response.getResponse != null && response.getResponse.get("debug") != null) {
      generated = true
      debugInfo.processSolrResponse(response.getResponse.get("debug").asInstanceOf[NamedList[AnyRef]])
    }

    if (generated) {
      Some(debugInfo)
    } else {
      None
    }
  }

  def bulkUpdateFields(version: Int) = ContextAction.async(parse.json(maxLength = 1024 * 2000)) { implicit context => implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>

      val products = productList.products
      if (products.size > MaxProductIndexBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxProductIndexBatchSize")))
      } else {
        val storage = withNamespace(storageFactory)
        val future = storage.updateProductFields(products:_*) flatMap { result =>

          val futureList = storage.findRawProducts(products.map(p => p.getId)) map { products => {
            val updatedProductList = new ProductList(products.toSeq, productList.feedTimestamp)
            val skuDocs = updatedProductList.toDocuments(categoryService)
            if (!skuDocs.isEmpty) {
              val productUpdate = new ProductUpdate
              productUpdate.add(skuDocs)
              productUpdate.process(solrServer)
            }
           }
          }

          futureList map { resultSolrUpdate =>
            Created
          }
        }

        withErrorHandling(future, s"Cannot update fields for products with ids [${products map (_.getId) mkString ","}]")
      }
    }.recoverTotal {
      case e: JsError =>
        Future.successful(BadRequest(Json.obj(
          // @TODO figure out how to pull missing field from JsError
          "message" -> ("Missing required fields " + e.toString) )))
    }
  }


  def bulkCreateOrUpdate(version: Int) = ContextAction.async(parse.json(maxLength = 1024 * 2000)) { implicit context => implicit request =>
    Json.fromJson[ProductList](request.body).map { productList =>
      val products = productList.products
      if (products.size > MaxProductIndexBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "message" -> s"Exceeded number of products. Maximum is $MaxProductIndexBatchSize")))
      } else {
        try {
          val skuDocs = productList.toDocuments(categoryService)
          val storage = withNamespace(storageFactory)
          val productFuture = storage.saveProduct(products:_*)
          var futureList: List[Future[AnyRef]] = List(productFuture)

          if (!skuDocs.isEmpty) {
            val productUpdate = new ProductUpdate
            productUpdate.add(skuDocs)
            val searchFuture: Future[UpdateResponse] = productUpdate.process(solrServer)
            val suggestionFuture = IndexableElement.addToIndex(filterProducts(productList.products), fetchCount = false, productList.feedTimestamp)
            futureList = List(productFuture,searchFuture, suggestionFuture)
          }

          val future: Future[Result] = Future.sequence(futureList) map { result =>
            Created
          }

          withErrorHandling(future, s"Cannot store products with ids [${products map (_.id.get) mkString ","}]")
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

  private def filterProducts(products: Seq[Product]): Seq[Product] = {
    products.filter(p => !p.isOem.getOrElse(false) && p.skus.getOrElse(Seq.empty[Sku]).map(s => s.hasNonPoos).contains(true))
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
    var future: Future[Result] = null
    if (context.isPublic && context.lang.language == Lang.English) {
      val updateSuggestionsQuery = new AsyncUpdateRequest()
      updateSuggestionsQuery.setParam("collection", SuggestCollection)
      updateSuggestionsQuery.deleteByQuery("+type:product -feedTimestamp:" + feedTimestamp)
      future = update.process(solrServer).flatMap( response => {
        updateSuggestionsQuery.process(solrServer).map{response => NoContent }
      })
    } else {
      future = update.process(solrServer).map( response => {
        NoContent
      })
    }
    withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
  }

  object FromValue extends Enumeration {
    type FormValue = Value
    val index, storage, all = Value
  }

  @ApiOperation(value = "Deletes products", notes = "Deletes the given product", httpMethod = "DELETE")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "feedTimestamp", value = "The feed timestamp. If provided, only skus with a different timestamp are deleted", required = false, dataType = "long", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Deletes products in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query"),
    new ApiImplicitParam(name = "from", value = "Determine if the product will be deleted from index, storage or both", defaultValue = "index", required = false, dataType = "String", paramType = "query")
  ))
  def deleteById(
      version: Int = 1,
      @ApiParam(value = "A product id", required = false)
      @PathParam("id")
      id: String) = ContextAction.async { implicit context => implicit request =>
    val timer = new Timer()
    val from = FromValue.withName(request.getQueryString("from").getOrElse("index"))
    var future: Future[Result] = null
    if (!from.equals(FromValue.storage)) {
      val update = new ProductUpdate()
      val feedTimestamp = request.getQueryString("feedTimestamp")

      if (feedTimestamp.isDefined) {
        update.deleteByQuery(s"productId:$id AND -indexStamp: ${feedTimestamp.get}")
      } else {
        update.deleteByQuery(s"productId:$id")
      }
      future = update.process(solrServer).flatMap {
        response => {
          Logger.info(s"Deleting product $id from index")
          if (from.equals(FromValue.all)) {
            deleteFromStorage(id,timer)
          } else {
            Future.successful(NoContent)
          }
        }
      }
    } else {
      future = deleteFromStorage(id,timer)
    }
    withErrorHandling(future, s"Cannot delete product [$id  ]")
  }

  def deleteFromStorage(id: String, timer:Timer)(implicit context: Context, request: Request[AnyContent]):Future[Result] ={

    val storage = withNamespace(storageFactory)
    Logger.info(s"Deleting product $id from storage")
    storage.deleteProduct(id).map { lastError =>
      if (lastError.ok) {
        NoContent
      } else {
        InternalServerError(Json.obj(
          "metadata" -> Json.obj(
          "time" -> timer.stop()),
          "message" -> s"Unable to delete product $id from storage"))
      }
    }
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
    val timer = new Timer()
    val query = new SolrQuery(q)

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
    val solrQuery = withPagination(withFields(query, request.getQueryString("fields"))).setParam("collection", searchCollection.name(request2lang))
    solrQuery.setRequestHandler("suggest")
    val future: Future[Result] = solrServer.query(solrQuery).flatMap( response => {
      if (query.getRows > 0) {
        var unexpectedErrorMessage = s"Unexpected response found for query '$q'"
        processSearchResults(null, response, unexpectedErrorMessage).map { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              withCorsHeaders(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> timer.stop()),
              "suggestions" -> Json.toJson(
                products map (Json.toJson(_))))))
            } else {
              withCorsHeaders(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> timer.stop()),
                "suggestions" -> Json.arr()
              )))
            }
          } else {
            Logger.debug(s"Unexpected response found for query $q")
            withCorsHeaders(InternalServerError(Json.obj(
              "metadata" -> Json.obj(
                "time" -> timer.stop()),
              "message" -> "Unable to execute query")))
          }
        }
      } else {
        Future.successful(withCorsHeaders(Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> response.getResults.getNumFound,
            "time" -> timer.stop())))))
      }
    })
    withErrorHandling(future, s"Cannot search for [$q]", request)
  }

  @ApiOperation(value = "Searchs Product Generations", notes = "Returns products with same generation productId", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findByMasterId(
              version: Int,
              @ApiParam(value = "Product Id", required = true)
              @QueryParam("id")
              id: String,
              @ApiParam(value = "Site to search", required = true)
              @QueryParam("site")
              site: String) = ContextAction.async { implicit context =>  implicit request =>

    val timer = new Timer()
    val query = new ProductSearchQuery(s"generation_master:$id AND -productId:$id", site)
      .withPagination()
      .withGrouping()
      .withOutlet()
    query.addSort("generation_number", SolrQuery.ORDER.desc)
    val future: Future[Result] = solrServer.query(query).flatMap( response => {
      if (query.getRows > 0) {
        val unexpectedErrorMessage = s"Unexpected response found for product '$id'"
        processSearchResults(site, response, unexpectedErrorMessage).map { case (found, products, groupSummary) =>
          if (products != null) {
            if (found > 0) {
              withCorsHeaders(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> timer.stop()),
                "products" -> Json.toJson(
                  products map (Json.toJson(_))))))
            } else {
              withCorsHeaders(Ok(Json.obj(
                "metadata" -> Json.obj(
                  "found" -> found,
                  "time" -> timer.stop()),
                "products" -> Json.arr()
              )))
            }
          } else {
            Logger.debug(s"No generations found for product $id")
            withCorsHeaders(NotFound(Json.obj(
              "metadata" -> Json.obj(
                "time" -> timer.stop()),
              "message" -> s"Cannot find generations for product id [$id]")))
          }
        }
      } else {
        Future.successful(withCorsHeaders(Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> response.getResults.getNumFound,
            "time" -> timer.stop())))))
      }
    })
    withErrorHandling(future, s"Cannot find products for [$id]", request)
  }

  
  @ApiOperation(value = "Find similar products", notes = "Returns similar products for a given product", response = classOf[Product], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete product result list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of products", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "metadata", value = "Comma delimited metadata fields list", required = false, dataType = "string", paramType = "metadata"),
    new ApiImplicitParam(name = "filterQueries", value = "Filter queries from a facet filter", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findSimilarProducts(
      version: Int,
      @ApiParam(value = "Find products similar", required = true)
      @PathParam("id")
      id: String
      @ApiParam(value = "Site to browse", required = false),
      @QueryParam("site")
      site: String) = ContextAction.async { implicit context => implicit request =>
    Logger.debug(s"Find similar products $id")
    withErrorHandling(findMoreLikeThis(version, id, site), s"Cannot find similar products for [$id]", request)
  }

  private def findMoreLikeThis(version: Int, productId: String, site: String)(implicit context: Context, request: Request[AnyContent]) = {
    val startTime = System.currentTimeMillis()
    val query = new ProductMoreLikeThisQuery(productId, site)
      .withGrouping()
      .withPagination()
      .withFilterQueries()

    solrServer.query(query).flatMap { response =>
      if (query.getRows > 0) {
        val docResponse = response.getResults
        if (docResponse == null || docResponse.getNumFound == 0) {
          Logger.debug(s"Cannot find similar products for product: [$productId]")
          Future(withCorsHeaders(NotFound(Json.obj("messages" -> s"Cannot find similar products for product: [$productId]"))))
        } else {
          val productIds = new jutil.ArrayList[(String, String)]
          docResponse.foreach(product => {
            productIds.add((product.getFieldValue("productId").asInstanceOf[String], product.getFieldValue("id").asInstanceOf[String]))
          })
          val storage = withNamespace(storageFactory)

          var productFuture: Future[Iterable[Product]] = null
          val fields = fieldList(allowStar = true)
          if (site != null) {
            productFuture = storage.findProducts(productIds, site, context.lang.country, fields, minimumFields = true, context.isPreview)
          } else {
            productFuture = storage.findProducts(productIds, context.lang.country, fields, minimumFields = true, context.isPreview)
          }

          productFuture.map(products => {
            //TODO gsegura: when the MoreLikeThisHandler in solr supports adding components to the processing pipeline, like the search handler does
            //refactor this code so we get the summary from the group collapse component, instead of getting it from mongo.
            //SOLR ticket: https://issues.apache.org/jira/browse/SOLR-5480
            val metadataFields = StringUtils.split(request.getQueryString("metadata").getOrElse(""), ',')
            val summary = if (metadataFields.isEmpty || metadataFields.contains("productSummary")) createProductSummary(products) else None

            withCacheHeaders(buildSearchResponse(
              query = query,
              found = Some(docResponse.getNumFound),
              productSummary = summary,
              startTime = Some(startTime),
              products = Some(products map (Json.toJson(_)))), products map (_.getId))
          })
        }
      } else {
        Future.successful(buildSearchResponse(query = query, found = Some(0), startTime = Some(startTime),
          message = Some("No products found")))
      }
    }
  }

  @ApiOperation(value = "Find product contents", notes = "Returns product content for given product", response = classOf[ProductContent], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "preview", value = "Display preview results", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def findProductContent(
    version: Int,
    @ApiParam(value = "Find product content", required = true)
    @PathParam("id") id: String 
    @ApiParam(value = "Site to browse", required = false),
    @QueryParam("site") site: String) = ContextAction.async { 
    implicit context => implicit request =>
      Logger.debug(s"Query product $id")

      val timer = Timer()
      val startTime = Some(System.currentTimeMillis())
      val storage = withNamespace(storageFactory)
      val contentIds = StringUtils.split(id, ",").map(i => (i, null))
      val contentFuture = if (site != null) {
        storage.findContent(contentIds, site)
      } else {
        storage.findContent(contentIds)
      }

      val future = contentFuture.map(content => {
        if (content.size > 0) {
          Logger.debug("Found ProductContent " + id)
          Ok(Json.obj(
            "metadata" -> Json.obj("time" -> timer.stop(), "found" -> content.size),
            "productContents" -> Json.toJson(content))
          )
        } else {
          Logger.debug("ProductContent " + id + " not found")
          NotFound(Json.obj(
            "metadata" -> Json.obj("time" -> timer.stop()),
            "message" -> s"Cannot find product contents"
          ))
        }
      })

      withErrorHandling(future, s"Cannot retrieve product contents", request)
  }

  @ApiOperation(value = "Deletes product content by Id", notes = "Deletes the content for given product", httpMethod = "DELETE")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "feedTimestamp", value = "The feed timestamp. If provided, only content with a different timestamp are deleted", required = false, dataType = "long", paramType = "query"),
    new ApiImplicitParam(name = "preview", value = "Deletes products in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def deleteContentById(
    version: Int = 1,
    @ApiParam(value = "A content id", required = true)
    @PathParam("id") id: String 
    @ApiParam(value = "Site to browse", required = true),
    @QueryParam("site") site: String) = ContextAction.async { 
    implicit context => implicit request =>
      val timer = new Timer()
      val feedTimestamp = request.getQueryString("feedTimestamp")
      var future: Future[Result] = null
 
      if (feedTimestamp.isDefined) {
        val timestamp = {
          try {
            feedTimestamp.get.toLong
          } catch {
            case e: Exception => 0
          }
        } : Long

        future = deleteContentFromStorage(timer, id, timestamp, site)
      } else {
        future = deleteContentFromStorage(timer, id = id, site = site)
      }

      withErrorHandling(future, s"Cannot delete product contents before feed timestamp [$feedTimestamp], site [$site], id [$id]")
  }
  
  @ApiOperation(value = "Deletes content", notes = "Delete content that was not updated in a given feed", httpMethod = "DELETE")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "preview", value = "Delete content in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  def deleteContentByTimestamp(
    version: Int = 1,
    @ApiParam(value = "The feed timestamp. All content with a different timestamp are deleted", required = true)
    @QueryParam("feedTimestamp") feedTimestamp: Long
    @ApiParam(value = "Site to browse", required = true),
    @QueryParam("site") site: String) = ContextAction.async { 
    implicit context => implicit request =>
      val timer = new Timer()
      var future: Future[Result] = deleteContentFromStorage(timer, feedTimestamp = feedTimestamp, site = site)

      withErrorHandling(future, s"Cannot delete product contents before feed timestamp [$feedTimestamp], site [$site]")
  }

  def deleteContentFromStorage(timer:Timer, id: String = null, feedTimestamp: Long = 0, site: String)(implicit context: Context, request: Request[AnyContent]):Future[Result] ={
    val storage = withNamespace(storageFactory)
    var deleteOperation : Future[LastError] = null;
    if (StringUtils.isNotBlank(site)) {
      Logger.info(s"Deleting product content from storage with id [$id] site [$site] and timestamp [$feedTimestamp]")
      deleteOperation = storage.deleteContent(id, feedTimestamp, site);
    } else {
      Logger.info(s"Deleting product content from storage with  id [$id] timestamp [$feedTimestamp]")
      deleteOperation = storage.deleteContent(id, feedTimestamp);
    }

    deleteOperation.map { lastError =>
      if (lastError.ok) {
        NoContent
      } else {
        InternalServerError(Json.obj(
          "metadata" -> Json.obj(
          "time" -> timer.stop()),
          "message" -> s"Unable to delete product content $id from storage"))
      }
    }
  }

  @ApiOperation(value = "Creates product content", notes = "Creates/updates content for the given products", httpMethod = "PUT")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "contents", value = "Content to create/update", required = true, dataType = "org.opencommercesearch.api.models.ProductContent", paramType = "body"),
    new ApiImplicitParam(name = "preview", value = "Create product content in preview", defaultValue = "false", required = false, dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 400, message = "Missing required fields"),
    new ApiResponse(code = 400, message = "Exceeded maximum number of contents that can be created at once")
  ))
  def bulkCreateOrUpdateProductContent(
    version: Int = 1,
    @ApiParam(value = "Site to browse", required = true)
    @QueryParam("site") site: String) = ContextAction.async(parse.json(maxLength = 1024 * 2000)) {
    implicit context => implicit request =>
      Json.fromJson[ProductContentList](request.body).map { contentList =>
        val timer = Timer()
        val contents = contentList.contents
        val feedTimestamp = contentList.feedTimestamp

        if (contents.size > MaxProductIndexBatchSize) {
          Future.successful(BadRequest(Json.obj("message" -> s"Exceeded number of product contents. Maximum is $MaxProductIndexBatchSize")))
        } else {
          try {
            val storage = withNamespace(storageFactory)
            val contentFuture = storage.saveProductContent(feedTimestamp, site, contents:_*)
            val future: Future[Result] = contentFuture map { result =>
              Created
            }

            withErrorHandling(future, s"Cannot store product contents with ids [${contents map (_.id.get) mkString ","}]")
          } catch {
            case e: IllegalArgumentException =>
              Logger.error(e.getMessage)
              Future.successful(BadRequest(Json.obj("message" -> e.getMessage)))
          }
      }
    }.recoverTotal {
      case e: JsError =>
        Future.successful(BadRequest(Json.obj("message" -> "Missing required fields")))
    }
  }
}
