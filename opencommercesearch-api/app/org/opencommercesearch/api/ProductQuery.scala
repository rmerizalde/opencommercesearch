package org.opencommercesearch.api

import play.api.{Configuration, Play}
import play.api.mvc.{AnyContent, Request}

import java.net.URLDecoder

import org.opencommercesearch.api.Global.{DefaultPaginationLimit, MaxPaginationLimit, _}
import org.opencommercesearch.api.common.FilterQuery
import org.opencommercesearch.common.Context

import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.{GroupParams, ExpandParams}

import play.api.Logger

/**
 * The base query to retrieve product results
 *
 * @param q is the query
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 * @author rmerizalde
 */
object ProductQuery {

  def trimq(q: String) : String = {
    if (q.length < MaxQueryLength) q
    else{
      Logger.warn("Trimming query too long.")
      q.substring(0, MaxQueryLength)
    }
  }

  def trimfq(q: String) : String = {
    if (q.length < MaxFilterQueryLength) q
    else {
        Logger.warn("Trimming filter query too long.")
        val index = q.lastIndexOf('|', MaxFilterQueryLength)
        q.substring(0, if (index < 0) MaxFilterQueryLength else index)
    }
  }

}

sealed class ProductQuery(q: String, site: String = null)(implicit context: Context, request: Request[AnyContent] = null) extends SolrQuery(ProductQuery.trimq(q)) {
  import Query._
  import org.opencommercesearch.api.Collection._

  private var _filterQueries: Array[FilterQuery] = null
  private val closeoutSites = Play.current.configuration.getString("sites.closeout").getOrElse("").split(",").toSet
  private val groupingFilters = Play.current.configuration.getConfig("search.group.collapse.fq").getOrElse(Configuration.empty)
  protected val metadataFields = if (request != null) StringUtils.split(request.getQueryString("metadata").getOrElse(""), ',') else Array.empty[String]


  protected def init() : Unit = {
    setFields("id")
    setParam("collection", searchCollection.name(context.lang))

    if (site == null || closeoutSites.contains(site)) {
      addFilterQuery("isRetail:true")
    }

    // product params
    addFilterQuery(s"country:${context.lang.country}")

    // RuleComponent params
    setParam("rule", true)
    setParam("siteId", site)
    // @todo add to API interface
    setParam("catalogId", site)
    withPagination(0, DefaultPaginationLimit)
  }

  init()

  def filterQueries = _filterQueries

  def withPagination() : ProductQuery = {
    var offset = 0
    var limit = DefaultPaginationLimit

    if (request != null) {
      for (o <- request.getQueryString("offset")) {
        offset = o.toInt
      }
      for (l <- request.getQueryString("limit")) {
        limit = l.toInt
      }
    }

    withPagination(offset, limit)
  }

  def withPagination(offset: Int, limit: Int) : ProductQuery = {
    setStart(offset)
    setRows(Math.min(MaxPaginationLimit, limit))
    this
  }

  def facets = metadataFields.isEmpty || metadataFields.contains("facets")

  def withFaceting(field: String = "category", limit: Option[Int] = None) : ProductQuery = {
    setFacet(facets)
    if (facets) {
      addFacetField(s"{!ex=collapse}$field")
      setFacetMinCount(1)

      limit foreach { l =>
        setFacetLimit(l)
      }
    }

    this
  }

  def withSpellCheck(enabled: Boolean = true) = {
    this.setParam("spellcheck", enabled)
    this
  }

  def withFilterQueries() : ProductQuery = {
    _filterQueries = setFilterQueriesFor(this)


    //if the preview=true query parameter is true, then don't apply the launch date filter query
    if (FilterLiveProductsEnabled && ! context.isPreview) {
      this.addFilterQuery("-launchDate:[NOW/HOUR TO *]")
    }

    defaultFilterQueriesParams.foreach(fq => {
      this.addFilterQuery(fq)
    })
    this
  }

  def withSorting() : ProductQuery = {
    val encodedSortParam = if(request != null) request.getQueryString("sort").getOrElse("") else ""
    val sortParam = URLDecoder.decode(encodedSortParam, "UTF-8")
    val sortSpecs = StringUtils.split(sortParam, ",").map(s => s.trim)
    if (sortSpecs != null && sortSpecs.length > 0) {
      val country = context.lang.country
      for (sortSpec <- sortSpecs) {
        val selectedOrder = if (sortSpec.endsWith(" asc")) SolrQuery.ORDER.asc else SolrQuery.ORDER.desc

        if (sortSpec.startsWith("discountPercent")) {
          addSort(s"discountPercent$country$currentSite", selectedOrder)
        } else if (sortSpec.startsWith("reviewAverage")) {
          addSort("bayesianReviewAverage", selectedOrder)
        } else if (sortSpec.startsWith("price")) {
          addSort(s"salePrice$country$currentSite", selectedOrder)
        } else if (sortSpec.startsWith("activationDate")) {
          addSort("activationDate", selectedOrder)
        } else if (sortSpec.startsWith("bestSeller")) {
          addSort(s"sellRank$site", selectedOrder)
        } else if (sortSpec.startsWith("revenue")) {
          addSort(s"revenue$site", selectedOrder)
        } else if (sortSpec.startsWith("id ")) {
          addSort("id", selectedOrder)
        }
      }
    }

    this
  }

  def withDebugInfo() : ProductQuery = {
    if ("true".equals(request.getQueryString("debug").getOrElse(""))) {
      add("debug.explain.structured", "true")
      add("debugQuery", "true")
      add("debugRule", "true")
    }
    this
  }

  def groupTotalCount = metadataFields.isEmpty || metadataFields.contains("found")

  def withGrouping() : ProductQuery = {
    val collapse = metadataFields.isEmpty || metadataFields.contains("productSummary")
    val limit = if (collapse) 50 else 1
    withGrouping(groupTotalCount, limit, collapse)
  }

  /**
   * Sets parameters to group skus by product
   *
   * @param totalCount if the query should return the total number of groups (products)
   * @param limit the minimum number of skus to group by product
   * @param collapse if the query should output summary fields for prices and discount percentage
   *
   * @return this query
   */
  def withGrouping(totalCount: Boolean, limit: Int, collapse: Boolean) : ProductQuery = {
    val groupMethod = if (request == null) null else request.getQueryString("group.method").orNull
    withGrouping(groupMethod, groupTotalCount, limit, collapse)
  }

  private def currentSite = {
    import play.api.Play.current
    if (Play.configuration.getBoolean("siteSpecificFields").getOrElse(false)) site else StringUtils.EMPTY
  }

  /**
   * Sets parameters to group skus by product
   *
   * @param groupMethod the group method to use: "filter" or "group"
   * @param totalCount if the query should return the total number of groups (products)
   * @param limit the minimum number of skus to group by product
   * @param collapse if the query should output summary fields for prices and discount percentage
   *
   * @return this query
   */
  def withGrouping(groupMethod:String, totalCount: Boolean, limit: Int, collapse: Boolean) : ProductQuery = {
    if (getRows == null || getRows > 0) {

      if ("filter" == groupMethod) {
        addField("productId")
        addFilterQuery("{!collapse field=productId tag=collapse}")
        set(ExpandParams.EXPAND + "all", true)
        set(ExpandParams.EXPAND_FIELD)
        set(ExpandParams.EXPAND_ROWS, limit)
        set(ExpandParams.EXPAND_SORT, s"isCloseout asc, discountPercent${context.lang.country}$currentSite desc, sort asc, score desc")
      } else {
        set(GroupParams.GROUP, true)
        set(GroupParams.GROUP_FIELD, "productId")
        set(GroupParams.GROUP_TOTAL_COUNT, totalCount)
        set(GroupParams.GROUP_LIMIT, limit)
        set(GroupParams.GROUP_FACET, false)
        set(GroupParams.GROUP_SORT, s"isCloseout asc, discountPercent${context.lang.country}$currentSite desc, sort asc, score desc")
      }
    }

    setParam("groupcollapse", collapse)
    if (collapse) {
      val country = context.lang.country
      val listPrice = s"listPrice$country"
      val salePrice = s"salePrice$country$currentSite"
      val discountPercent = s"discountPercent$country$currentSite"
      val isOnSale = s"onsale$country$currentSite"

      val collapseMethod = if (request == null) null else request.getQueryString("groupcollapse.method").orNull

      setParam("groupcollapse.fl", s"$listPrice,$salePrice,$discountPercent,color,colorFamily,isRetail,isOutlet,$isOnSale")

      // this is a temporal param, after testing it will either be removed or made permanent for filter grouping
      if ("ords" == collapseMethod) {
        groupingFilters.getString(site) foreach { groupingFilter =>
          setParam("groupcollapse.fq", groupingFilter)
        }
        set(s"f.$listPrice.sf", "min", "max")
        set(s"f.$salePrice.sf", "min", "max")
        set(s"f.$discountPercent.sf", "min", "max")
        set("f.color.sf", "count")
        set("f.colorFamily.sf", "distinct")
        set("f.isRetail.sf", "bucket")
        set("f.isOutlet.sf", "bucket")
        set(s"f.$isOnSale.sf", "bucket")
      } else {
        setParam("groupcollapse.ff", "isCloseout")
      }
    }

    this
  }

  def withRules(ruleFilters: Seq[String]) : ProductQuery = {
    setParam("rulePage", true)
    setQuery("*:*")

    for (rules <- ruleFilters) {
      if(rules.nonEmpty) {
        addFilterQuery(rules.substring(rules.indexOf(":") + 1, rules.length()))
      }
    }
    this
  }

  def withBrand(brandId : String) : ProductQuery = {
    addFilterQuery(s"brandId:$brandId")
    setParam("brandId", brandId)
    this
  }

  def withAncestorCategory(categoryId : String) : ProductQuery = {
    addFilterQuery(s"ancestorCategoryId:$categoryId")
    this
  }

  def withOutlet() : ProductQuery = {
    if(request != null && request.getQueryString("outlet").getOrElse("false").toBoolean) {
      addFilterQuery("isOutlet:true")
    }
    this
  }

  def withRedirects() : ProductQuery = {
    if(!(request != null && request.getQueryString("redirects").getOrElse("true").toBoolean)) {
      setParam("redirects", "false")
    }
    this
  }

  def withoutToos() : ProductQuery = {
    addFilterQuery("isToos:false")
    this
  }

  def withCustomParams() : ProductQuery  = {
    SearchCustomParams.map(param =>
        request.queryString.map(qparam =>
          if(qparam._1.equalsIgnoreCase(param)) {
            setParam(qparam._1, qparam._2:_*)
          }
        )
    )
    this
  }

}

private object Query {
  val Score = "score"

  /**
   * handles multiple encodings and special characters
   * like +, % etc.
   * @param url the encoded Url
   * @param encoding the encoding type
   *
   * @return the decoded Url
   */
  def decodeUrl(url: String, encoding: String): String = {
    try {
      if (url == null || url.indexOf('%') == -1) {
        return url
      }
      val decoded = URLDecoder.decode(url,encoding)
      if (decoded == url) {
        return url
      }
      decodeUrl(decoded, encoding)
    } catch {
      case iae: IllegalArgumentException =>
        url
    }
  }

  def setFilterQueriesFor(query : SolrQuery)(implicit request: Request[AnyContent]) = {
    val fqStr = decodeUrl(request.getQueryString("filterQueries").getOrElse(""), "UTF-8")
    val filterQueries = FilterQuery.parseFilterQueries(ProductQuery.trimfq(fqStr))

    query.remove("rule.fq")
    filterQueries.foreach(fq => {
      query.add("rule.fq", fq.toString)
     })
    filterQueries
  }
}

/**
 * A search query
 *
 * @param q is the query
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 */
class ProductSearchQuery(q: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends ProductQuery(q, site) {
  protected override def init() : Unit = {
    super.init()
    setParam("pageType", "search")
  }
}

/**
 * A more like this query
 *
 * @param pid the product we are using to find other similar products
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 */
class ProductMoreLikeThisQuery(pid: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends ProductQuery(s"productId:$pid", site) {
  protected override def init() : Unit = {
    super.init()
    setRequestHandler("/mlt")
    if (StringUtils.isNotBlank(site)) {
      //return only products for the given site
      addFilterQuery(s"category:0.$site")
    }
    //skip toos products from the similar results
    addFilterQuery("isToos:false")
    //exclude from similar results documents with the same productId
    addFilterQuery(s"-productId:$pid")
  }

  override def withGrouping(): ProductQuery = {
    withGrouping("filter", groupTotalCount, 1, collapse = false)
  }
}

/**
 * A browse query
 *
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 */
class ProductBrowseQuery(site: String)(implicit context: Context, request: Request[AnyContent]) extends ProductQuery("*:*", site) {

  protected override def init() : Unit = {
    super.init()
    setParam("pageType", "category")
  }

  override def withOutlet() : ProductQuery = {
     request.getQueryString("outlet") match {
      case Some(isOutlet) => addFilterQuery(s"isOutlet:$isOutlet")
      case _ =>
        val isOnSaleParamName = "onsale" + context.lang.country
        val isOnSaleParam = request.getQueryString("onsale")
        isOnSaleParam match {
          case Some(isOnSale) => addFilterQuery(s"$isOnSaleParamName:$isOnSale")
          case _ => addFilterQuery("isOutlet:false")
        }
     }
     this
   }
}

/**
 * A query to retrieve a single product
 *
 * @param productId is the product id
 * @param site is the site to search in
 * @param context is the search context
 */
class SingleProductQuery(productId : String, site : String)(implicit context: Context) extends SolrQuery("*:*") {
  import org.opencommercesearch.api.Collection._

  private def init() : Unit = {
    setParam("collection", searchCollection.name(context.lang))
    addFilterQuery(s"productId:$productId")
    setRows(1)

    if (site != null) {
       addFilterQuery(s"categoryPath:$site")
     }
  }

  init()

  def withFields(field: String*) : SingleProductQuery = {
    setFields(field:_*)
    this
  }
}

/**
 * A convenient query to retrieve a field facet on products
 *
 * @param facetField is the facet's field
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 */
class ProductFacetQuery(facetField: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends SolrQuery("*:*") {
  import Query._
  import org.opencommercesearch.api.Collection._

  private val closeoutSites: Set[String] = Play.current.configuration.getString("sites.closeout").getOrElse(StringUtils.EMPTY).split(",").toSet

  def this(facetField: String)(implicit context: Context, request: Request[AnyContent]) = this(facetField, null)

  private def init() = {
    setParam("collection", searchCollection.name(context.lang))
    setRows(0)
    setFacet(true)
    addFacetField(s"{!ex=collapse}$facetField")
    setFacetMinCount(1)

    if (closeoutSites.contains(site)) {
      addFilterQuery("isRetail:true")
    }

    // product params
    addFilterQuery(s"country:${context.lang.country}")

    if (site != null) {
      addFilterQuery("categoryPath:" + site)
    }
    if (request != null) {

      request.getQueryString("outlet") match {
	    case Some(isOutlet) =>
	      addFilterQuery(s"isOutlet:$isOutlet")
	    case _ =>
	      val isOnSaleParamName = "onsale" + context.lang.country
	      val isOnSaleParam = request.getQueryString("onsale")
	      isOnSaleParam match {
	        case Some(isOnSale) => addFilterQuery(s"$isOnSaleParamName:$isOnSale")
	        case _ => addFilterQuery("isOutlet:false")
	      }
	  }
    } else {
      if (closeoutSites.contains(site)) {
        addFilterQuery("isOutlet:false")
      }
    }
  }

  init()

  def withPagination() : ProductFacetQuery = {
    var offset = "0"
    var limit = DefaultPaginationLimit

    for (o <- request.getQueryString("offset")) { offset = o }
    for (l <- request.getQueryString("limit")) { limit = l.toInt }

    setParam("facet.offset", offset)
    setFacetLimit(Math.min(MaxBrandPaginationLimit, limit))
    this
  }

  def withFilterQueries() : ProductFacetQuery = {
    setFilterQueriesFor(this)
    this
  }

  def withPagination(offset: Int, limit: Int) : ProductFacetQuery = {
    setParam("facet.offset", offset.toString)
    setFacetLimit(Math.min(MaxPaginationLimit, limit))
    this
  }

  def withBrand(brandId : String) : ProductFacetQuery = {
    addFilterQuery(s"brandId:$brandId")
    this
  }

  def withAncestorCategory(categoryId : String) : ProductFacetQuery = {
    addFilterQuery(s"ancestorCategoryId:$categoryId")
    this
  }

  def withFacetPrefix(prefix : String) : ProductFacetQuery = {
    setFacetPrefix(prefix)
    this
  }

}
