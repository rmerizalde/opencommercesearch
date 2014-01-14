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

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap

import org.opencommercesearch.api.models.{Facet, Filter}
import org.opencommercesearch.api.util.Util
import org.apache.solr.common.params.FacetParams
import org.apache.solr.common.util.NamedList
import org.apache.solr.client.solrj.response.{RangeFacet, QueryResponse}
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField.Count
import org.apache.commons.lang3.StringUtils


/**
 * @author rmerizalde
 */
case class FacetHandler (
  query: SolrQuery,
  queryResponse: QueryResponse,
  filterQueries: Array[FilterQuery],
  facetData: Seq[NamedList[AnyRef]]) {

  def getFacets : Seq[Facet] = {
    val facetMap: Map[String, Facet] = new HashMap[String, Facet]

    for (facetField <- queryResponse.getFacetFields) {
      val facet = createFacet(facetField.getName)

      for (f <- facet) {
        val filters = new ArrayBuffer[Filter](facetField.getValueCount)
        val prefix: String = query.getFieldParam(f.getFieldName, FacetParams.FACET_PREFIX)
        val facetBlackList: Set[String] = getBlacklist(facetField.getName)
        for (count <- facetField.getValues) {
          val filterName: String = getCountName(count, prefix)
          if (!facetBlackList.contains(filterName)) {
            val filter = new Filter(Some(filterName), Some(count.getCount),
              Some(getCountPath(count, f, filterQueries)),
              Some(count.getAsFilterQuery),
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
    rangeFacets(facetMap)
    queryFacets(facetMap)

    val sortedFacets = new ArrayBuffer[Facet](facetMap.size)
    sortedFacets.appendAll(facetMap.values)
    sortedFacets.filter(facet => {
      var include = false
      for (filters <- facet.filters) {
        include = filters.size >= facet.minBuckets.get
      }
      include
    }).map(facet => {
      // hide fields need internally only
      facet.fieldName = None
      facet.isHardened = None
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

    /*
    for (fieldName <- Array[String]("category", "categoryPath")) {
      val facet = facetMap.get(fieldName)
      for (f <- facet) {
        sortedFacets.append(f)
      }
    }
    import scala.collection.JavaConversions._
    for (fieldName <- manager.facetFieldNames) {
      val facet: Facet = facetMap.get(fieldName)
      if (facet != null) {
        sortedFacets.add(facet)
      }
    }
    return sortedFacets*/
  }

  private def rangeFacets(facetMap: Map[String, Facet]) : Unit = {
    if (queryResponse.getFacetRanges != null) {
      for (range <- queryResponse.getFacetRanges) {
        val facet = createFacet(range.getName)
        for (f <- facet) {
          val filters: Seq[Filter] = new ArrayBuffer()

          val beforeFilter = createBeforeFilter(range, f)
          if (beforeFilter != null) {
            filters.add(beforeFilter)
          }

          var prevCount: RangeFacet.Count = null
          for (count <- range.getCounts) {
            if (prevCount == null) {
              prevCount = count
            } else {
              filters.add(createRangeFilter(range.getName, f, Util.ResourceInRange,
                prevCount.getValue, count.getValue, prevCount.getCount))
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
            filters.add(createRangeFilter(range.getName, f, Util.ResourceInRange, prevCount.getValue,
              value2.toString, prevCount.getCount))
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
    val v1 = removeDecimals(value1)
    val v2 = removeDecimals(value2)
    val filterQuery = s"$fieldName:[$v1 TO v2]"
    new Filter(
      Some(Util.getRangeName(fieldName, key, v1, v2, null)),
      Some(count),
      Some(getCountPath(fieldName, filterQuery, facet)),
      Some(filterQuery),
      None
    )
  }

  private def removeDecimals(number: String) : String = {
    val index = number.indexOf(".")
    if (index != -1) {
      number.substring(0, index)
    } else {
      number
    }
  }

  private def queryFacets(facetMap: Map[String, Facet]): Unit = {
    val queryFacets = queryResponse.getFacetQuery

    if (queryFacets != null) {
      var facet: Facet = null
      var facetFieldName = StringUtils.EMPTY
      var filters: ArrayBuffer[Filter] = null

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
              facet = createFacet(fieldName).getOrElse(null)
              filters = new ArrayBuffer[Filter]()

              facet.filters = Some(filters)
              facetMap.put(fieldName, facet)
            }

            val filterQuery = fieldName + ':' + expression

            val filter = new Filter(
              Some(FilterQuery.unescapeQueryChars(Util.getRangeName(fieldName, expression))),
              Some(count),
              Some(getCountPath(expression, filterQuery, facet)),
              Some(filterQuery),
              None)
            filter.setSelected(fieldName, expression, filterQueries)
            filters.add(filter)
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
   * Creates a new facet from the given facet field definition
   */
  private def createFacet(fieldName: String) : Option[Facet] = {
    val facetDefinition = facetData.find(nl => fieldName.equals(nl.get(Facet.FieldName)))
    var facet: Facet = null

    for (fd <- facetDefinition) { facet = Facet.fromDefinition(fd.asInstanceOf[NamedList[String]]) }
    Option.apply(facet)
  }

  /**
   * @todo jmendez, we need to export the blacklist in the feed. Blacklist is irrelevant to
   *       Solr and can be written to mongo for client side filtering
   */
  private def getBlacklist(fieldName: String) : Set[String] = {
    Set.empty
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
    val fieldName = facet.fieldName.getOrElse(null)
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
}
