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


case class Sku(
  var id: Option[String],
  var season: Option[String],
  var year: Option[String],
  var image: Option[Image],
  var countries: Option[Seq[Country]],
  var isPastSeason: Option[Boolean],
  var colorFamily: Option[String],
  var color: Option[String],
  var isRetail: Option[Boolean],
  var isCloseout: Option[Boolean],
  var isOutlet: Option[Boolean],
  var size: Option[Size],
  var catalogs: Option[Seq[String]],
  var customSort: Option[Int],
  var listPrice: Option[BigDecimal],
  var salePrice: Option[BigDecimal],
  var discountPercent: Option[Int],
  var onSale: Option[Boolean],
  var stockLevel: Option[Int],
  var url: Option[String],
  var allowBackorder: Option[Boolean]) {

  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None,
      None, None, None, None, None)
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

