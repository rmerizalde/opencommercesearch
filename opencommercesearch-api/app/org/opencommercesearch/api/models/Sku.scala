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

import play.api.libs.json.{Json}
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import Sku._
import com.fasterxml.jackson.annotation.JsonCreator
import org.jongo.marshall.jackson.oid.Id
import com.fasterxml.jackson.annotation.JsonProperty

case class Sku(
  @Id var id: Option[String],
  @JsonProperty("season") var season: Option[String],
  @JsonProperty("year") var year: Option[String],
  @JsonProperty("image") var image: Option[Image],
  @JsonProperty("countries") var countries: Option[Seq[Country]],
  @JsonProperty("isPastSeason") var isPastSeason: Option[Boolean],
  @JsonProperty("colorFamily") var colorFamily: Option[String],
  @JsonProperty("isRetail") var isRetail: Option[Boolean],
  @JsonProperty("isCloseout") var isCloseout: Option[Boolean],
  @JsonProperty("isOutlet") var isOutlet: Option[Boolean],
  @JsonProperty("size") var size: Option[Size],
  @JsonProperty("catalogs") var catalogs: Option[Seq[String]],
  @JsonProperty("customSort") var customSort: Option[Int],
  @JsonProperty("listPrice") var listPrice: Option[BigDecimal],
  @JsonProperty("salePrice") var salePrice: Option[BigDecimal],
  @JsonProperty("discountPercent") var discountPercent: Option[Int],
  @JsonProperty("onSale") var onSale: Option[Boolean],
  @JsonProperty("stockLevel") var stockLevel: Option[Int],
  @JsonProperty("url") var url: Option[String],
  @JsonProperty("allowBackorder") var allowBackorder: Option[Boolean]) {

  @JsonCreator
  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None,
      None, None, None, None)
}

object Sku {
  val ListPrice = "listPrice"
  val SalePrice = "salePrice"
  val DiscountPercent = "discountPercent"
  val Url = "url"
  val Stocklevel = "stockLevel"

  implicit val readsSku = Json.reads[Sku]
  implicit val writesSku = Json.writes[Sku]
}

