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

import org.apache.solr.client.solrj.beans.Field
import play.api.libs.json.Json

case class SolrSku (
  var id: Option[String],
  var title: Option[String],
  var image: Option[String],
  var isToos: Option[Boolean],
  var isPastSeason: Option[Boolean],
  var isCloseout: Option[Boolean],
  var reviews: Option[Int],
  var bayesianReviewAverage: Option[Float],
// @TODO implement a better way to handle site specific properties
  var hasFreeGiftbcs: Option[Boolean]) {

  def this() = this(None, None, None, None, None, None, None, None, None)


  @Field
  def setId(id: String) = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) = { this.title = Option.apply(title) }

  @Field
  def setImage(image: String) = { this.image = Option.apply(image) }

  @Field("isToos")
  def setToos(isToos: Boolean) = { this.isToos = Option.apply(isToos) }

  @Field("isPastSeason")
  def setPastSeason(isPastSeason: Boolean) = { this.isPastSeason = Option.apply(isPastSeason) }

  @Field ("isCloseout")
  def setCloseout(isCloseout: Boolean) = { this.isCloseout = Option.apply(isCloseout) }

  @Field
  def setReviews(reviews: Int) = { this.reviews = Option.apply(reviews) }

  @Field
  def setBayesianReviewAverage(bayesianReviewAverage: Float) = { this.bayesianReviewAverage = Option.apply(bayesianReviewAverage) }

  @Field("freeGiftbcs")
  def setFreeGiftbcs(hasFreeGiftbcs: Boolean) = { this.hasFreeGiftbcs = Option.apply(hasFreeGiftbcs) }
}

object SolrSku {
  implicit val readsSku = Json.reads[SolrSku]
  implicit val writesSku = Json.writes[SolrSku]
}



