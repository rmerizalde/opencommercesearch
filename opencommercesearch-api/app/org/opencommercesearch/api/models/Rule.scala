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
import play.api.libs.functional.syntax._

import java.util

import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.beans.Field
import scala.collection.generic.SeqFactory
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
 * @param siteId is a list of target siteId to which the rules will apply to
 * @param catalogId is a list of catalogId to which the rules will apply to (you may have more than one catalog on per site)
 * @param category is a list of category and tokens to which the rules should apply.
 * @param boostFunction is a boost function to be applied by this rule
 * @param facets list of facets to be created by this rule
 *
 */
case class Rule(
  var id: Option[String],
  var query: Option[String],
  var sortPriority: Option[Int],
  var combineMode: Option[String],
  var startDate: Option[String],
  var endDate: Option[String],
  var target: Option[Array[String]],
  var siteId: Option[Array[String]],
  var catalogId: Option[Array[String]],
  var category: Option[Array[String]],
  var boostFunction: Option[String],
  var facets: Option[Array[String]]) {

  def  this() = this(None, None, None, None, None, None, None, None, None, None, None, None)

  def getId() : String = { id.get }

  @Field
  def setId(id: String) : Unit = {
    this.id = Option.apply(id)
  }

  def getQuery() : String = { query.get }

  @Field
  def setQuery(query: String) : Unit = { this.query = Option.apply(query) }

  def getSortPriority() : Int = { sortPriority.get }

  @Field
  def setSortPriority(sortPriority: Int) : Unit = { this.sortPriority = Option.apply(sortPriority) }

  def getCombineMode() : String = { combineMode.get }

  @Field
  def setCombineMode(combineMode: String) : Unit = { this.combineMode = Option.apply(combineMode) }

  def getStartDate() : String = { startDate.get }

  @Field
  def setStartDate(startDate: Date) : Unit = { this.startDate = Option.apply(startDate.toString) }

  def getEndDate() : String = { endDate.get }

  @Field
  def setEndDate(endDate: Date) : Unit = { this.endDate = Option.apply(endDate.toString) }

  def getTarget() : Array[String] = { target.get }

  @Field
  def setTarget(target: Array[String]) : Unit = { this.target = Option.apply(target) }

  def getSiteId() : Array[String] = { siteId.get }

  @Field
  def setSiteId(siteId: Array[String]) : Unit = { this.siteId = Option.apply(siteId) }

  def getCatalogId() : Array[String] = { catalogId.get }

  @Field
  def setCatalogId(catalogId: Array[String]) : Unit = { this.catalogId = Option.apply(catalogId) }

  def getCategory() : Array[String] = { category.get }

  @Field
  def setCategory(category: Array[String]) : Unit = { this.category = Option.apply(category) }

  def getBoostFunction() : String = { boostFunction.getOrElse(null) }

  @Field
  def setBoostFunction(boostFunction: String) : Unit = { this.boostFunction = Option.apply(boostFunction) }

  def getFacets() : Array[String] = { facets.getOrElse(null) }

  @Field("facetField")
  def setFacets(facets: Array[String]) : Unit = { this.facets = Option.apply(facets) }
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



