package org.opencommercesearch.api.common

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

import java.net.URLEncoder
import java.text.ParseException

import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField.Count
import org.apache.solr.client.solrj.response.{QueryResponse, RangeFacet}
import org.apache.solr.common.params.FacetParams
import org.apache.solr.common.util.NamedList
import org.opencommercesearch.api.Global.FacetTtl
import org.opencommercesearch.api.common.FacetHandler.{EmptyBlackList, FacetBlackListFields}
import org.opencommercesearch.api.models.{BreadCrumb, Facet, Filter}
import org.opencommercesearch.api.service.Storage
import org.opencommercesearch.api.util.Util
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import reactivemongo.core.commands.LastError

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author rmerizalde
 */
case class FacetHandler (
  query: SolrQuery,
  queryResponse: QueryResponse,
  filterQueries: Array[FilterQuery],
  facetData: Seq[NamedList[AnyRef]],
  storage: Storage[LastError]) {

  def getFacets : Future[Seq[Facet]] = {
    val facetMap: mutable.LinkedHashMap[String, Facet] = new mutable.LinkedHashMap[String, Facet]
    
    //To preserve the order of the facets first we need to initialize the keys of the linked hash map in the correct order
    //from results in the Solr rule_facet. Then in a second pass we'll populate the actual facet and filter values
    facetMap.put("category", null)
    val facetIds = facetData map { entry =>
      val name = entry.get(Facet.FieldName).asInstanceOf[String]
      if (name == null) {
        throw new NullPointerException("Facet name can't be null")
      }
      //Get the facet ID too, so we can look it up on storage to get the blacklist
      val id = entry.get(Facet.Id)
      if (id == null) {
        throw new NullPointerException("Facet id can't be null")
      }

      facetMap.put(name, null)
      id.asInstanceOf[String]
    }

    fieldFacets(facetMap, facetIds) map { _ =>
      rangeFacets(facetMap)
      queryFacets(facetMap)

      val sortedFacets = new mutable.ArrayBuffer[Facet](facetMap.size)
      //remove any possible facets that are null cause they were in the rule_facet but not in the solr response
      sortedFacets.appendAll(facetMap.values.filterNot(value => null == value))

      sortedFacets.filter(facet => {
        var include = false

        for (filters <- facet.filters) {
          include = filters.size >= facet.minBuckets.get
        }
        include
      }).map(facet => {
        // hide fields need internally only
        facet.id = None
        facet.fieldName = None
        facet.isHardened = None
        facet.isByCountry = None
        facet.isBySite = None
        facet.start = None
        facet.end = None
        facet.gap = None
        facet.minBuckets = None
        facet.minCount = None
        facet.isMissing = None
        facet.limit = None
        facet.sort = None
        facet
      })
    }
  }

  private def fieldFacets(facetMap: mutable.Map[String, Facet], facetIds: Seq[String]) : Future[Unit] = {
    var categoryPrefix = StringUtils.EMPTY
    if (queryResponse.getResponse != null && queryResponse.getResponse.get("category_prefix") != null) {
      categoryPrefix = queryResponse.getResponse.get("category_prefix").asInstanceOf[String]
    }
    
    if (queryResponse.getFacetFields != null) {
      retrieveFacetBlacklists(facetIds) map { _ =>
        for (facetField <- queryResponse.getFacetFields) {
          var facet: Option[Facet] = None

          if (facetField.getName == "category") {
            facet = createCategoryFacet(facetField.getName)
          }
          else {
            facet = createFacet(facetField.getName)
          }

          for (f <- facet) {
            val filters = new mutable.ArrayBuffer[Filter](facetField.getValueCount)
            val prefix = query.getFieldParam(f.getFieldName, FacetParams.FACET_PREFIX)
            var facetBlackList: Set[String] = EmptyBlackList

            //If this is a category facet, then the ID will be None.
            for (facetId <- f.id) {
              facetBlackList = Cache.getAs[Set[String]](facetId).getOrElse(EmptyBlackList)
            }

            for (count <- facetField.getValues) {
              val filterName: String = getCountName(count, if (StringUtils.isNotEmpty(categoryPrefix)) categoryPrefix else prefix)
              if (facetBlackList == null || !facetBlackList.contains(filterName)) {
                val filter = new Filter(Some(filterName), Some(count.getCount),
                  Some(URLEncoder.encode(getCountPath(count, f, filterQueries), "UTF-8")),
                  Some(URLEncoder.encode(count.getAsFilterQuery, "UTF-8")),
                  None
                )
                filter.setSelected(count.getFacetField.getName, filterName, filterQueries)
                filters.append(filter)
              }
            }
            f.filters = Some(filters)
            facetMap.put(facetField.getName, f)
          }
        }
      }
    } else {
      Future.successful(())
    }
  }

  private def originalFieldName(fieldName: String): String = {
    val facetDefinition = facetData.find(nl => fieldName.equals(nl.get(Facet.FieldName)))

    facetDefinition match {
      case Some(fd) =>
        val originalFieldName = fd.get("originalFieldName").asInstanceOf[String]
        if (originalFieldName != null) originalFieldName else fieldName
      case None => fieldName
    }
  }

  private def rangeFacets(facetMap: mutable.Map[String, Facet]) : Unit = {
    if (queryResponse.getFacetRanges != null) {
      for (range <- queryResponse.getFacetRanges) {
        val facet = createFacet(range.getName)
        for (f <- facet) {
          val filters = new mutable.ArrayBuffer[Filter]()

          val beforeFilter = createBeforeFilter(range, f)
          if (beforeFilter != null) {
            filters.add(beforeFilter)
          }

          var prevCount: RangeFacet.Count = null
          for (count <- range.getCounts) {
            if (prevCount == null) {
              prevCount = count
            } else {
              val rangeFilter = createRangeFilter(originalFieldName(range.getName), f, Util.ResourceInRange,
                prevCount.getValue, count.getValue, prevCount.getCount)
              filters.add(rangeFilter)
              prevCount = count
            }
          }

          if (prevCount != null) {
            val hardened = f.isHardened.getOrElse(false)
            var value2 = f.end.getOrElse("0").toInt
            if (!hardened) {
              val gap = f.gap.getOrElse("0").toInt
              value2 = Math.round(prevCount.getValue.toFloat)
              value2 += gap
            }
            
            val filter = createRangeFilter(range.getName, f, Util.ResourceInRange, prevCount.getValue, value2.toString, prevCount.getCount)
            if (filter != null){
                filters.add(filter)
            }
          }

          val afterFilter = createAfterFilter(range, f)
          if (afterFilter != null) {
            filters.add(afterFilter)
          }

          f.filters = Some(filters)
          facetMap.put(range.getName, f)
        }
      }
    }
  }

  private def createBeforeFilter(range: RangeFacet[_, _], facet: Facet): Filter = {
    if (range.getBefore == null || range.getBefore.intValue() == 0) {
      null
    } else {
      val rangeStart = facet.start.getOrElse(-1)
      createRangeFilter(range.getName, facet, Util.ResourceBefore, "*", rangeStart.toString, range.getBefore.intValue())
    }
  }

  private def createAfterFilter(range: RangeFacet[_, _], facet: Facet): Filter = {
    if (range.getAfter == null || range.getAfter.intValue() == 0) {
      null
    } else {
      val rangeEnd = facet.end.getOrElse(-1)
      createRangeFilter(range.getName, facet, Util.ResourceAfter, rangeEnd.toString, "*", range.getAfter.intValue())
    }
  }

  private def createRangeFilter(fieldName: String, facet: Facet, key: String, value1: String, value2: String, count: Int): Filter = {
    var filter: Filter = null
    try {
      val v1 = removeDecimals(value1)
      val v2 = removeDecimals(value2)
      val filterQuery = s"$fieldName:[$v1 TO v2]"
      val filterName = Util.getRangeName(originalFieldName(fieldName), key, v1, v2, null)
      filter = new Filter(
        Some(filterName),
        Some(count),
        Some(URLEncoder.encode(getCountPath(fieldName, filterQuery, facet), "UTF-8")),
        Some(URLEncoder.encode(filterQuery, "UTF-8")),
        None
      )
    } catch {
        case ex: ParseException =>
          Logger.error("Invalid range expression for fieldName: " + fieldName + " and key: " + key)
    }
    filter
  }

  private def removeDecimals(number: String) : String = {
    val index = number.indexOf(".")
    if (index != -1) {
      number.substring(0, index)
    } else {
      number
    }
  }

  private def queryFacets(facetMap: mutable.Map[String, Facet]): Unit = {
    val queryFacets = queryResponse.getFacetQuery

    if (queryFacets != null) {
      var facet: Facet = null
      var facetFieldName = StringUtils.EMPTY
      var filters: mutable.ArrayBuffer[Filter] = null

      for (entry <- queryFacets.entrySet) {
        val count: Int = entry.getValue
        if (count > 0) {
          val query = entry.getKey
          val parts = getFacetQueryParts(query)

          if (parts != null) {
            val fieldName = parts(0)
            val expression = parts(1)

            if (!facetFieldName.equals(fieldName)) {
              facetFieldName = fieldName
              facet = createFacet(fieldName).orNull
              if (facet != null) {
                filters = new mutable.ArrayBuffer[Filter]()
                facet.filters = Some(filters)
                facetMap.put(fieldName, facet)
              }
            }

            if (facet != null) {
              val filterQuery = fieldName + ':' + expression
              try{
                  val filter = new Filter(
                    Some(FilterQuery.unescapeQueryChars(Util.getRangeName(originalFieldName(fieldName), expression))),
                    Some(count),
                    Some(URLEncoder.encode(getCountPath(expression, filterQuery, facet), "UTF-8")),
                    Some(URLEncoder.encode(filterQuery, "UTF-8")),
                    None)
                  filter.setSelected(fieldName, expression, filterQueries)
                  filters.add(filter)
              } catch {
                case ex: ParseException =>
                  Logger.error("Invalid range expression for fieldName: " + fieldName + " and expression: " + expression)
              }
            }
          }
        }
      }
    }
  }

  private def getFacetQueryParts(query: String): Array[String] = {
    val parts = StringUtils.split(query, ':')
    if (parts.length == 2) {
      val index = parts(0).indexOf('}')
      if (index != -1) {
        parts(0) = parts(0).substring(index + 1)
      }
      parts
    } else {
      null
    }
  }
  
   /**
   * Creates a new category facet with the default facet values
   */
  private def createCategoryFacet(fieldName: String) : Option[Facet] = {
    val categoryFacet = Facet.getInstance()
    categoryFacet.name = Option.apply(fieldName)
    categoryFacet.fieldName = Option.apply(fieldName)
    categoryFacet.minBuckets = Option.apply(1)
    categoryFacet.minCount = Option.apply(1)
    categoryFacet.isMultiSelect = Option.apply(false)
    categoryFacet.isMixedSorting = Option.apply(false)
    Option.apply(categoryFacet)
  }
  
  /**
   * Creates a new facet from the given facet field definition
   */
  private def createFacet(fieldName: String) : Option[Facet] = {
    val facetDefinition = facetData.find(nl => fieldName.equals(nl.get(Facet.FieldName)))
    var facet: Facet = null

    for (fd <- facetDefinition) { facet = Facet.fromDefinition(fd.asInstanceOf[NamedList[String]]) }
    Option.apply(facet)
  }

  /**
   * Populates the cache with blacklists for the given facet ids
   * @param ids List of ids to return facets from
   * @return a future boolean indicating whether the facet were retrieved from the storage or not
   */
  private def retrieveFacetBlacklists(ids: Seq[String]) : Future[Boolean] = {
    val retrieveBlackLists = ids.exists(id => Cache.get(id).isEmpty)

    if (retrieveBlackLists) {
      storage.findFacets(ids, FacetBlackListFields) map { facets =>
        val foundIds = facets.map(facet => facet.getId).toSet

        facets.foreach(facet => {
          val blackList = if (facet.getBlackList != null) facet.getBlackList.toSet else EmptyBlackList
          Cache.set(facet.getId, blackList, FacetTtl)
        })

        ids.foreach(id => if (!foundIds.contains(id)) {
          Cache.set(id, EmptyBlackList, FacetTtl)
        })
        true
      }
    } else {
      Future.successful(false)
    }
  }

  /**
   * Return the count name. If the count name starts with the facet prefix it will be removed from the name
   */
  def getCountName(count: Count, prefix: String): String = {
    var name = count.getName

    if (prefix != null && name.startsWith(prefix)) {
      name = name.substring(prefix.length())
    }
    name
  }

  private def getCountPath(count: Count, facet: Facet, filterQueries: Array[FilterQuery]) : String = {
    getCountPath(count.getName, count.getAsFilterQuery, facet)
  }

  /**
   * Returns the path with all filter queries to select/deselect the given count. If the count filter query is already
   * in the list of filterQueries then the generated path will be for de-selection. Otherwise, the path is for selected
   * the facet value. The exception two this rule are overlapping facets. Overlapping facets are typically range filter
   * queries which are subset of each other. For example, lets take a field discountPercent and the query facet like this:
   *
   * <ul>
   *     <li>[10 TO *]</li>
   *     <li>[20 TO *]</li>
   *     <li>[30 TO *]</li>
   *     <li>[40 TO *]</li>
   *     <li>[50 TO *]</li>
   * </ul>
   *
   * If you select 10 TO * all other facets are still applicable. The same happens if you select 50 To *. Note that the
   * filtered results may still be different but all facet apply. This facets should no be configured with the multi-
   * select option. If that's the case, the path will always generate a selection. However, any previous selection will
   * be overriden.
   *
   * @param countName is the count's name
   * @param countFilterQuery the count's filter query
   * @param facet is the count's parent facet
   * @return
   */
  private def getCountPath(countName: String, countFilterQuery: String, facet: Facet): String = {
    val fieldName = facet.fieldName.orNull
    var selectedFilterQuery: FilterQuery = null
    var replacementFilterQuery: String = null

    if (filterQueries != null) {
      for (query <- filterQueries) {
        if (!facet.isMultiSelect.getOrElse(false) && query.fieldName.equals(fieldName)) {
          selectedFilterQuery = query
          replacementFilterQuery = countFilterQuery
        } else if (query.fieldName.equals(fieldName) && query.unescapeExpression.equals(countName)) {
          selectedFilterQuery = query
        }
      }
    }

    val path = Util.createPath(filterQueries, selectedFilterQuery, replacementFilterQuery)
    if (selectedFilterQuery != null) {
      path
    } else if (StringUtils.isNotBlank(path)) {
      path + FilterQuery.PathSeparator + countFilterQuery
    } else {
      countFilterQuery
    }
  }

  /**
   * From the filterQueries selected by the user, generate a list of BreadCrumbs
   * to allow un-selecting a given facet.
   *
   * @return a list of breadcrumbs
   */
  def getBreadCrumbs: Seq[BreadCrumb] = {
    val crumbs =  mutable.ArrayBuffer[BreadCrumb]()
    if (filterQueries == null || filterQueries.length == 0) {
      return crumbs
    }
    filterQueries.foreach(filterQuery => {
      if ("category".equals(filterQuery.fieldName)) {
        crumbs.addAll(createCategoryBreadCrumb(filterQuery))
      } else {
        try{
          val crumb = new BreadCrumb(
            Some(filterQuery.fieldName),
            Some(getCrumbExpression(filterQuery.fieldName, filterQuery.unescapeExpression)),
            Some(URLEncoder.encode(Util.createPath(filterQueries, filterQuery), "UTF-8"))
          )
          crumbs.add(crumb)
        } catch {
          case ex: ParseException =>
            Logger.error("Invalid range expression for fieldName: " + filterQuery.fieldName + " and expression: " + filterQuery.unescapeExpression)
        }
      }
    })
    crumbs
  }

  /**
   * Creates the bread crumbs for the selected categories
   *
   * @param categoryFilterQuery
   *            the category filter query selected
   */
  private def createCategoryBreadCrumb(categoryFilterQuery: FilterQuery): Seq[BreadCrumb] = {
    if (categoryFilterQuery == null) {
      return Seq.empty
    }
    val categories = StringUtils.split(categoryFilterQuery.expression, FilterQuery.CategorySeparator)
    if (categories.length <= 2) {
      return Seq.empty
    }
    val breadCrumbs =  mutable.ArrayBuffer[BreadCrumb]()
    val catalogId: String = categories(1)
    val buffer = new StringBuffer()
    val basePath = Util.createPath(filterQueries, categoryFilterQuery)
    var level = 1
    for (i <- 2 until categories.length) {
      var unselectPath = StringUtils.EMPTY
      if (buffer.length() > 0) {
        unselectPath += "category:" + level + FilterQuery.CategorySeparator + catalogId + buffer.toString
        level += 1
      }

      if (StringUtils.isNotBlank(basePath)) {
        if (StringUtils.isNotBlank(unselectPath)) {
          unselectPath += FilterQuery.PathSeparator
        }
        unselectPath += basePath
      }

      val category = categories(i)
      val crumb = new BreadCrumb(
        Some(categoryFilterQuery.fieldName),
        Some(FilterQuery.unescapeQueryChars(category)),
        Some(URLEncoder.encode(unselectPath, "UTF-8"))
      )

      breadCrumbs.add(crumb)
      buffer.append(FilterQuery.CategorySeparator).append(category)
    }
    breadCrumbs
  }
  
  @throws(classOf[ParseException])
  private def getCrumbExpression(fieldName: String, expression: String): String = {
    val name = originalFieldName(fieldName)
    Util.getRangeName(name, Util.getRangeBreadCrumb(name, expression, expression))
  }
}

object FacetHandler {

  /**
   * The empty blacklist
   */
  val EmptyBlackList = Set.empty[String]
  /**
   * The default list of fields to retrieve a facet blacklist
   */
  val FacetBlackListFields = Seq("id", "blackList")
}
