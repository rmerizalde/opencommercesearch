package org.opencommercesearch.api.models

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

import play.api.libs.json._
import org.apache.solr.client.solrj.beans.Field
import org.apache.solr.common.util.NamedList
import org.jongo.marshall.jackson.oid.Id
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a single Facet
 * @param id is the system id of the Facet
 * @param name Display name of the facet
 * @param type Type of the facet (range, date, field, query, ...)
 * @param uiType Type of UI that should be used (hidden, reviews, colors, ...)
 * @param isMultiSelect Whether or not this facet supports multi select
 * @param minBuckets Min buckets (or filters) to show. If less than this number are found, no facet should be created.
 * @param fieldName On field facets, this is the field name.
 * @param isMixedSorting Whether or not the facet should use a mixed sorting approach.
 * @param minCount Min count per bucket. If less than this number documents are found for a bucket, the bucket should not be created.
 * @param sort Sort mechanism. Can be count (default), or by index - i.e. alphabetically.
 * @param isMissing Whether or not a "missing" bucket should be shown for the facet.
 * @param limit Max number of buckets to show.
 * @param start On range facets, this is the start range.
 * @param end On range facets, this is the end range.
 * @param gap On range facets, this is the gap between each bucket.
 * @param isHardened Whether or not a range facet is hardened. This means whether or not use the min possible value, or use the next "gap" when the end of the range is reached.
 * @param queries Array of queries used by query facets.
 * @param filters The actual list of filters for this facet. I.e. if you facet is person, the list of filters may be "susan, user, john"
 * @param blackList Black list of facet filters that should be ignored. For example, if your facet is person, you may want to exclude "user" from the filter list since is too common.
 */
case class Facet(
  @Id var id: Option[String],
  @JsonIgnore var name: Option[String],
  @JsonIgnore var `type`: Option[String],
  @JsonIgnore var uiType: Option[String],
  @JsonIgnore var isMultiSelect: Option[Boolean],
  @JsonIgnore var fieldName: Option[String],
  @JsonIgnore var minBuckets: Option[Int],
  @JsonIgnore var isMixedSorting: Option[Boolean],
  @JsonIgnore var minCount: Option[Int],
  @JsonIgnore var sort: Option[String],
  @JsonIgnore var isMissing: Option[Boolean],
  @JsonIgnore var limit: Option[Int],
  @JsonIgnore var start: Option[String],
  @JsonIgnore var end: Option[String],
  @JsonIgnore var gap: Option[String],
  @JsonIgnore var isHardened: Option[Boolean],
  @JsonIgnore var queries: Option[Array[String]],
  @JsonProperty("blackList") var blackList: Option[Seq[String]],
  @JsonIgnore  var filters: Option[Seq[Filter]]) {

  @JsonCreator
  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  def getId : String = { id.get }

  @Field
  def setId(id: String) : Unit = {
    this.id = Some(id)
  }

  def getName : String = { name.get }

  @Field
  def setName(name: String) : Unit = {
    this.name = Some(name)
  }

  def getType : String = { `type`.get }

  @Field
  def setType(`type`: String) : Unit = { this.`type`= Some(`type`) }

  def getUiType : String = { uiType.getOrElse(null) }

  @Field
  def setUiType(uiType: String) : Unit = {this.uiType = Some(uiType) }

  def getIsMultiSelect : java.lang.Boolean = { isMultiSelect.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsMultiSelect(isMultiSelect: Boolean) : Unit = {this.isMultiSelect = Some(isMultiSelect) }

  def getMinBuckets : Integer = { minBuckets.getOrElse(null).asInstanceOf[Integer] }

  @Field
  def setMinBuckets(minBuckets: Integer) : Unit = { this.minBuckets = Some(minBuckets) }

  def getFieldName : String = { fieldName.getOrElse(null) }

  @Field
  def setFieldName(fieldName: String) : Unit = { this.fieldName = Some(fieldName) }

  def getIsMixedSorting : java.lang.Boolean = { isMixedSorting.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsMixedSorting(isMixedSorting: Boolean) : Unit = { this.isMixedSorting = Some(isMixedSorting) }

  def getMinCount : Integer = { minCount.getOrElse(null).asInstanceOf[Integer] }

  @Field
  def setMinCount(minCount: Integer) : Unit = { this.minCount = Some(minCount) }

  def getSort : String = { sort.getOrElse(null) }

  @Field
  def setSort(sort: String) : Unit = { this.sort = Some(sort) }

  def getIsMissing : java.lang.Boolean = { isMissing.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsMissing(isMissing: Boolean) : Unit = { this.isMissing = Some(isMissing) }

  def getLimit : Integer = { limit.getOrElse(null).asInstanceOf[Integer] }

  @Field
  def setLimit(limit: Integer) : Unit = { this.limit = Some(limit) }

  def getStart : String = { start.getOrElse(null) }

  @Field
  def setStart(start: String) : Unit = { this.start = Some(start) }

  def getEnd : String = { end.getOrElse(null) }

  @Field
  def setEnd(end: String) : Unit = { this.end = Some(end) }

  def getGap : String = { gap.getOrElse(null) }

  @Field
  def setGap(gap: String) : Unit = { this.gap = Some(gap) }

  def getIsHardened : java.lang.Boolean = { isHardened.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsHardened(isHardened: Boolean) : Unit = { this.isHardened = Some(isHardened) }

  def getQueries : Array[String] = { queries.getOrElse(null) }

  @Field
  def setQueries(queries: Array[String]) : Unit = { this.queries = Some(queries) }

  def getBlackList: Seq[String] = { blackList.getOrElse(null) }

  def setBlackList(blackList: Seq[String]) : Unit = { this.blackList = Some(blackList) }
}

object Facet {
  val Id = "id"
  val Name = "name"
  val FieldName = "fieldName"
  val MinBuckets = "minBuckets"
  val IsMultiSelect = "isMultiSelect"
  val IsMixedSorting = "isMixedSorting"
  val UiType = "uiType"
  val IsHardened = "isHardened"
  var Start = "start"
  val End = "end"
  val Gap = "gap"
  val MinCount = "minCount"
  val Sort = "sort"
  val IsMissing = "isMissing"
  val Limit = "limit"

  def fromDefinition(facetDefinition: NamedList[String]): Facet = {
    val facet = new Facet

    facet.id = Some(facetDefinition.get(Id))
    facet.name = Some(facetDefinition.get(Name))
    facet.fieldName = Some(facetDefinition.get(FieldName))
    facet.sort = Some(facetDefinition.get(Sort))
    facet.start = Some(facetDefinition.get(Start))
    facet.end = Some(facetDefinition.get(End))
    facet.gap = Some(facetDefinition.get(Gap))
    facet.uiType = Some(facetDefinition.get(UiType))
    facet.isMultiSelect = Some("T".equals(facetDefinition.get(IsMultiSelect)))
    facet.isMixedSorting = Some("T".equals(facetDefinition.get(IsMixedSorting)))
    facet.isHardened = Some("T".equals(facetDefinition.get(IsHardened)))
    facet.isMissing = Some("T".equals(facetDefinition.get(IsMissing)))

    val minCount = facetDefinition.get(MinCount)
    if (minCount != null) { facet.minCount = Option.apply(minCount.toInt) }

    val minBuckets = facetDefinition.get(MinBuckets)
    facet.minBuckets = Option.apply(if (minBuckets != null) minBuckets.toInt else 2)

    val limit = facetDefinition.get(Limit)
    if (limit != null) { facet.limit = Option.apply(limit.toInt) }

    facet
  }

  implicit val readsFacet = Json.reads[Facet]
  implicit val writesFacet = Json.writes[Facet]
}

/**
 * Represents a list of Facets
 *
 * @param facets are the Facets in the list
 */
case class FacetList(facets: List[Facet]) {

}

/**
 * Helper class to hold facet blacklists from storage.
 * @param id The facet id
 * @param blackList The list of blacklisted values
 */
case class FacetBlackList(@Id var id: Option[String], @JsonProperty("blackList") var blackList: Option[Seq[String]]) {
}

object FacetList {
  implicit val readsFacetList = Json.reads[FacetList]
  implicit val writesFacetList = Json.writes[FacetList]
}