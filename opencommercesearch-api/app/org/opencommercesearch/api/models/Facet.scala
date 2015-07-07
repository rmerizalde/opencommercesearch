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

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}
import org.apache.solr.client.solrj.beans.Field
import org.apache.solr.common.util.NamedList
import play.api.libs.json._
import org.opencommercesearch.bson.BSONFormats._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

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
  var id: Option[String] = None,
  var name: Option[String] = None,
  var `type`: Option[String] = None,
  var uiType: Option[String] = None,
  var isMultiSelect: Option[Boolean] = None,
  var fieldName: Option[String] = None,
  var minBuckets: Option[Int] = None,
  var isMixedSorting: Option[Boolean] = None,
  var isByCountry: Option[Boolean] = None,
  var isBySite: Option[Boolean] = None,
  var minCount: Option[Int] = None,
  var sort: Option[String] = None,
  var isMissing: Option[Boolean] = None,
  var limit: Option[Int] = None,
  var start: Option[String] = None,
  var end: Option[String] = None,
  var gap: Option[String] = None,
  var isHardened: Option[Boolean] = None,
  var queries: Option[Array[String]] = None,
  var blackList: Option[Seq[String]] = None,
  var filters: Option[Seq[Filter]] = None) {

  def this() = this(id = None)

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

  def getIsByCountry : java.lang.Boolean = { isByCountry.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsByCountry(isByCountry: Boolean) : Unit = { this.isByCountry = Some(isByCountry) }

  def getIsBySite : java.lang.Boolean = { isBySite.getOrElse(null).asInstanceOf[java.lang.Boolean] }

  @Field
  def setIsBySite(isBySite: Boolean) : Unit = { this.isBySite = Some(isBySite) }

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
  val IsByCountry = "isByCountry"
  val IsBySite = "isBySite"
  val UiType = "uiType"
  val IsHardened = "isHardened"
  var Start = "start"
  val End = "end"
  val Gap = "gap"
  val MinCount = "minCount"
  val Sort = "sort"
  val IsMissing = "isMissing"
  val Limit = "limit"

  def getInstance() = new Facet()

  def fromDefinition(facetDefinition: NamedList[String]): Facet = {
    val minBuckets = facetDefinition.get(MinBuckets)
    val minCount = facetDefinition.get(MinCount)
    val limit = facetDefinition.get(Limit)

    new Facet(
      Option(facetDefinition.get(Id)),
      Option(facetDefinition.get(Name)),
      None,
      Option(facetDefinition.get(UiType)),
      Option("T".equals(facetDefinition.get(IsMultiSelect))),
      Option(facetDefinition.get(FieldName)),
      Option(if (minBuckets != null) minBuckets.toInt else 2),
      Option("T".equals(facetDefinition.get(IsMixedSorting))),
      Option("T".equals(facetDefinition.get(IsByCountry))),
      Option("T".equals(facetDefinition.get(IsBySite))),
      if (minCount != null) Some(minCount.toInt) else None,
      Option(facetDefinition.get(Sort)),
      Option("T".equals(facetDefinition.get(IsMissing))),
      if (limit != null) Some(limit.toInt) else None,
      Option(facetDefinition.get(Start)),
      Option(facetDefinition.get(End)),
      Option(facetDefinition.get(Gap)),
      Option("T".equals(facetDefinition.get(IsHardened))),
      None,
      None,
      None
    )
  }

  implicit val readsFacet = Json.reads[Facet]
  implicit val writesFacet = Json.writes[Facet]

  implicit object FacetWriter extends BSONDocumentWriter[Facet] {
    import reactivemongo.bson._

    def write(facet: Facet): BSONDocument = BSONDocument(
      "_id" -> facet.id,
      "name" -> facet.name,
      "type" -> facet.`type`,
      "uiType" -> facet.uiType,
      "isMultiSelect" -> facet.isMultiSelect,
      "fieldName" -> facet.fieldName,
      "minBuckets" -> facet.minBuckets,
      "isMixedSorting" -> facet.isMixedSorting,
      "isByCountry" -> facet.isByCountry,
      "isBySite" -> facet.isBySite,
      "minCount" -> facet.minCount,
      "sort" -> facet.sort,
      "isMissing" -> facet.isMissing,
      "limit" -> facet.limit,
      "start" -> facet.start,
      "end" -> facet.end,
      "gap" -> facet.gap,
      "isHardened" -> facet.isHardened,
      "queries" -> facet.queries,
      "blackList" -> facet.blackList,
      "filters" -> facet.filters

    )
  }

  implicit object FacetReader extends BSONDocumentReader[Facet] {
    def read(doc: BSONDocument): Facet = Facet(
      doc.getAs[String]("_id"),
      doc.getAs[String]("name"),
      doc.getAs[String]("type"),
      doc.getAs[String]("uiType"),
      doc.getAs[Boolean]("isMultiSelect"),
      doc.getAs[String]("fieldName"),
      doc.getAs[Int]("minBuckets"),
      doc.getAs[Boolean]("isMixedSorting"),
      doc.getAs[Boolean]("isByCountry"),
      doc.getAs[Boolean]("isBySite"),
      doc.getAs[Int]("minCount"),
      doc.getAs[String]("sort"),
      doc.getAs[Boolean]("isMissing"),
      doc.getAs[Int]("limit"),
      doc.getAs[String]("start"),
      doc.getAs[String]("end"),
      doc.getAs[String]("gap"),
      doc.getAs[Boolean]("isHardened"),
      doc.getAs[Array[String]]("queries"),
      doc.getAs[Seq[String]]("blacklist"),
      doc.getAs[Seq[Filter]]("filters")
    )
  }
}

/**
 * Represents a list of Facets
 *
 * @param facets are the Facets in the list
 */
case class FacetList(facets: List[Facet]) {

}

object FacetList {
  implicit val readsFacetList = Json.reads[FacetList]
  implicit val writesFacetList = Json.writes[FacetList]
}