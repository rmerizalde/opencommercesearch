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

import java.util.Date

import play.api.libs.json.Json

import com.fasterxml.jackson.annotation.JsonCreator

case class Sku(
  var id: Option[String] = None,
  var season: Option[String] = None,
  var year: Option[String] = None,
  var image: Option[Image] = None,
  var countries: Option[Seq[Country]] = None,
  var isPastSeason: Option[Boolean] = None,
  var color: Option[Color] = None,
  var title: Option[java.lang.String] = None,
  var isRetail: Option[Boolean] = None,
  var isCloseout: Option[Boolean] = None,
  var isOutlet: Option[Boolean] = None,
  var size: Option[Size] = None,
  var catalogs: Option[Seq[String]] = None,
  var listPrice: Option[BigDecimal] = None,
  var salePrice: Option[BigDecimal] = None,
  var discountPercent: Option[Int] = None,
  var onSale: Option[Boolean] = None,
  var url: Option[String] = None,
  var allowBackorder: Option[Boolean] = None,
  var availableDate: Option[Date] = None,
  var availability: Option[Availability] = None)
{
  def availabilityStatus = availability match {
    case Some(a) => a.status
    case None => None
  }
}

object Sku {
  val ListPrice = "listPrice"
  val SalePrice = "salePrice"
  val DiscountPercent = "discountPercent"
  val Url = "url"
  val Stocklevel = "stockLevel"

  @JsonCreator
  def getInstance() = new Sku()

  implicit val readsSku = Json.reads[Sku]
  implicit val writesSku = Json.writes[Sku]
}



