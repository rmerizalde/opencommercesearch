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
import java.util.Date

/**
 * Represents a single Rule
 *
 * @param id is the system id of the Rule
 * @param query is the query for the Rule
 * @param sortPriority is the sort priority for the Rule (i.e. what order should this rule have)
 * @param combineMode is the combine mode for the Rule (replace or append)
 * @param startDate is the date this rule will start applying
 * @param endDate is the date this rule will stop applying
 * @param target is a list of target page types to which the rules will apply to
 * @param subTarget retail or outlet pages to which the rules will apply to
 * @param siteId is a list of target siteId to which the rules will apply to
 * @param catalogId is a list of catalogId to which the rules will apply to (you may have more than one catalog on per site)
 * @param category is a list of category and tokens to which the rules should apply.
 * @param boostFunction is a boost function to be applied by this rule
 * @param facetField list of facets to be created by this rule
 * @param facetId list of facet ids to be created by this rule
 * @param ruleType type of rule
 */
case class Rule(
  var id: Option[String],
  var query: Option[String],
  var sortPriority: Option[Int],
  var combineMode: Option[String],
  var startDate: Option[String],
  var endDate: Option[String],
  var target: Option[Array[String]],
  var subTarget: Option[Array[String]],
  var siteId: Option[Array[String]],
  var catalogId: Option[Array[String]],
  var category: Option[Array[String]],
  var brandId: Option[Array[String]],
  var experimental: Option[Boolean],
  var boostFunction: Option[String],
  var facetField: Option[Array[String]],
  var facetId: Option[Array[String]],
  var boostedProducts: Option[Array[String]],
  var blockedProducts: Option[Array[String]],
  var ruleType: Option[String],
  // @todo jmendez: rename to url
  var redirectUrl: Option[String]) {

  def  this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  def getId : String = { id.get }

  @Field
  def setId(id: String) : Unit = {
    this.id = Option.apply(id)
  }

  def getQuery : String = { query.get }

  @Field
  def setQuery(query: String) : Unit = { this.query = Option.apply(query) }

  def getSortPriority : Integer = { sortPriority.getOrElse(null).asInstanceOf[Integer] }

  @Field
  def setSortPriority(sortPriority: Int) : Unit = { this.sortPriority = Option.apply(sortPriority) }

  def getCombineMode : String = { combineMode.getOrElse(null) }

  @Field
  def setCombineMode(combineMode: String) : Unit = { this.combineMode = Option.apply(combineMode) }

  def getStartDate : String = { startDate.getOrElse(null) }

  @Field
  def setStartDate(startDate: Date) : Unit = { this.startDate = Option.apply(startDate.toString) }

  def getEndDate : String = { endDate.get }

  @Field
  def setEndDate(endDate: Date) : Unit = { this.endDate = Option.apply(endDate.toString) }

  def getTarget : Array[String] = { target.get }

  @Field
  def setTarget(target: Array[String]) : Unit = { this.target = Option.apply(target) }

  def getSubTarget : Array[String] = { subTarget.get }
  
  @Field
  def setSubTarget(subTarget: Array[String]) : Unit = { this.subTarget = Option.apply(subTarget) }
  
  def getSiteId : Array[String] = { siteId.get }

  @Field
  def setSiteId(siteId: Array[String]) : Unit = { this.siteId = Option.apply(siteId) }

  def getCatalogId : Array[String] = { catalogId.get }

  @Field
  def setCatalogId(catalogId: Array[String]) : Unit = { this.catalogId = Option.apply(catalogId) }

  def getCategory : Array[String] = { category.get }

  @Field
  def setCategory(category: Array[String]) : Unit = { this.category = Option.apply(category) }

  def getBrandId : Array[String] = { brandId.get }

  @Field
  def setBrandId(brandId: Array[String]) : Unit = { this.brandId = Option.apply(brandId) }

  def getExperimental : Boolean = { experimental.getOrElse(false) }

  @Field
  def setExperimental(experimental: Boolean) : Unit = { this.experimental = Option.apply(experimental) }

  def getBoostFunction : String = { boostFunction.getOrElse(null) }

  @Field
  def setBoostFunction(boostFunction: String) : Unit = { this.boostFunction = Option.apply(boostFunction) }

  def getFacetField : Array[String] = { facetField.getOrElse(null) }

  @Field
  def setFacetField(facetField: Array[String]) : Unit = { this.facetField = Option.apply(facetField) }

  def getFacetId : Array[String] = { facetId.getOrElse(null) }

  @Field
  def setFacetId(facetId: Array[String]) : Unit = { this.facetId = Option.apply(facetId) }

  def getBoostedProducts : Array[String] = { boostedProducts.getOrElse(null) }

  @Field
  def setBoostedProducts(boostedProducts: Array[String]) : Unit = { this.boostedProducts = Option.apply(boostedProducts) }

  def getBlockedProducts : Array[String] = { blockedProducts.getOrElse(null) }

  @Field
  def setBlockedProducts(blockedProducts: Array[String]) : Unit = { this.blockedProducts = Option.apply(blockedProducts) }

  def getRuleType : String = { ruleType.getOrElse(null) }

  @Field
  def setRuleType(ruleType: String) : Unit = { this.ruleType = Option.apply(ruleType) }

  def getUrl : String = { redirectUrl.getOrElse(null) }

  @Field("redirectUrl")
  def setUrl(url: String) : Unit = { this.redirectUrl = Option.apply(url) }
}

object Rule {
  implicit val readsRule = Json.reads[Rule]
  implicit val writesRule = Json.writes[Rule]
}

/**
 * Represents a list of Rules
 *
 * @param rules are the Rules in the list
 */
case class RuleList(rules: List[Rule]) {

}

object RuleList {
  implicit val readsRuleList = Json.reads[RuleList]
  implicit val writesRuleList = Json.writes[RuleList]
}



