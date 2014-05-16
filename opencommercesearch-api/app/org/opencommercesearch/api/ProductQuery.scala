package org.opencommercesearch.api

import play.api.mvc.{AnyContent, Request}

import java.net.URLDecoder

import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.GroupParams
import org.opencommercesearch.common.Context
import org.opencommercesearch.api.common.FilterQuery
import org.opencommercesearch.api.Global.MaxPaginationLimit
import org.opencommercesearch.api.Global.DefaultPaginationLimit


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
sealed class ProductQuery(q: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends SolrQuery(q) {
  import Collection._
  import Query._

  val lang = context.lang

  private var _filterQueries: Array[FilterQuery] = null

  protected def init() : Unit = {
    setFields("id")
    setParam("collection", searchCollection.name(lang))

    // default filter queries
    addFilterQuery("isRetail:true")

    // product params
    addFilterQuery(s"country:${lang.country}")

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

    for (o <- request.getQueryString("offset")) { offset = o.toInt }
    for (l <- request.getQueryString("limit")) { limit = l.toInt }

    withPagination(offset, limit)
  }

  def withPagination(offset: Int, limit: Int) : ProductQuery = {
    setStart(offset)
    setRows(Math.min(MaxPaginationLimit, limit))
    this
  }

  def withFaceting() : ProductQuery = {
    setFacet(true)
    addFacetField("category")
    setFacetMinCount(1)
    this
  }

  def withFilterQueries() : ProductQuery = {
    _filterQueries = setFilterQueriesFor(this)
    this
  }

  def withSorting() : ProductQuery = {
    val sortParam = URLDecoder.decode(request.getQueryString("sort").getOrElse(""), "UTF-8")
    val sortSpecs = StringUtils.split(sortParam, ",")
    if (sortSpecs != null && sortSpecs.length > 0) {
      val country = lang.country
      for (sortSpec <- sortSpecs) {
        val selectedOrder = if (sortSpec.trim.endsWith(" asc")) SolrQuery.ORDER.asc else SolrQuery.ORDER.desc

        if (sortSpec.indexOf("discountPercent") != -1) {
          addSort(s"discountPercent$country", selectedOrder)
        }
        if (sortSpec.indexOf("reviewAverage") != -1) {
          addSort("bayesianReviewAverage", selectedOrder)
        }
        if (sortSpec.indexOf("price") != -1) {
          addSort(s"salePrice$country", selectedOrder)
        }
        if (sortSpec.indexOf("activationDate") != -1) {
          addSort("activationDate", selectedOrder)
        }
      }
    }

    if (getBool("group.sort") != null && isSortedByScore()) {
      remove("group.sort")
    }

    this
  }

  /**
   * @return <code>true</code> if this query is sorted by score. Otherwise <code>false</code>
   */
  private def isSortedByScore() = {
    val clauses = getSorts
    var isSortedByScore: Boolean = false
    if (clauses.size > 0) {
      import scala.collection.JavaConversions._
      for (clause <- clauses) {
        if (Score.equals(clause.getItem)) {
          isSortedByScore = true
        }
      }
    }
    else {
      isSortedByScore = true
    }
    isSortedByScore
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
  def withGrouping(totalCount: Boolean = true, limit: Int = 50, collapse: Boolean = true) : ProductQuery = {
    if (getRows == null || getRows > 0) {
      set(GroupParams.GROUP, true)
      set(GroupParams.GROUP_FIELD, "productId")
      set(GroupParams.GROUP_TOTAL_COUNT, totalCount)
      set(GroupParams.GROUP_LIMIT, limit)
      set(GroupParams.GROUP_FACET, false)

      setParam("groupcollapse", collapse)
      if (collapse) {
        val country = lang.country
        val listPrice = s"listPrice$country"
        val salePrice = s"salePrice$country"
        val discountPercent = s"discountPercent$country"

        setParam("groupcollapse.fl", s"$listPrice,$salePrice,$discountPercent")
        setParam("groupcollapse.ff", "isCloseout")
      }

      if (isSortedByScore()) {
        set("group.sort", "isCloseout asc, score desc, sort asc")
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
}

private object Query {
  val Score = "score"

  def setFilterQueriesFor(query : SolrQuery)(implicit request: Request[AnyContent]) = {
    val filterQueries = FilterQuery.parseFilterQueries(URLDecoder.decode(request.getQueryString("filterQueries").getOrElse(""), "UTF-8"))

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
    if(request != null && request.getQueryString("outlet").getOrElse("false").toBoolean) {
      addFilterQuery("isOutlet:true")
    }
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
    if (request.getQueryString("outlet").getOrElse("false").toBoolean ) {
      addFilterQuery("isOutlet:true")
    } else {
      addFilterQuery("isOutlet:false")
    }
  }
}

/**
 * A query to retrieve a single product
 *
 * @param productId is the product id
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 */
class SingleProductQuery(productId : String, site : String)(implicit context: Context, request: Request[AnyContent]) extends SolrQuery("*:*") {
  import Collection._
  import I18n._

  val lang = language()

  private def init() : Unit = {
    setParam("collection", searchCollection.name(lang))
    addFilterQuery(s"productId:$productId")
    setRows(1)

    if(site != null) {
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
  import Collection._
  import I18n._
  import Query._

  def this(facetField: String)(implicit context: Context, request: Request[AnyContent]) = this(facetField, null)

  val lang = language()

  private def init() = {
    setParam("collection", searchCollection.name(lang))
    setRows(0)
    setFacet(true)
    addFacetField(facetField)
    setFacetMinCount(1)
    if (site != null) {
      addFilterQuery("categoryPath:" + site)
    }
    addFilterQuery("isOutlet:"+request.getQueryString("outlet").getOrElse("false").toBoolean)
  }

  init()

  def withPagination() : ProductFacetQuery = {
    var offset = "0"
    var limit = DefaultPaginationLimit

    for (o <- request.getQueryString("offset")) { offset = o }
    for (l <- request.getQueryString("limit")) { limit = l.toInt }

    setParam("facet.offset", offset)
    setFacetLimit(Math.min(MaxPaginationLimit, limit))
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


