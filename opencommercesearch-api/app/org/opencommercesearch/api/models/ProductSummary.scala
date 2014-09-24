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

import play.api.libs.json.Json

sealed case class PriceSummary(var min: BigDecimal, var max: BigDecimal) {
  def process(price: BigDecimal) = {
    min = min.min(price)
    max = max.min(price)
  }
}

private object PriceSummary {
  implicit val readsPriceSummary = Json.reads[PriceSummary]
  implicit val writesPriceSummary = Json.writes[PriceSummary]
}

sealed case class DiscountSummary(var min: Int, var max: Int) {
  def process(discount: Int) = {
    min = min.min(discount)
    max = max.min(discount)
  }
}

private object DiscountSummary {
  implicit val readsDiscountSummary = Json.reads[DiscountSummary]
  implicit val writesDiscountSummary = Json.writes[DiscountSummary]
}

sealed case class ColorSummary(
  var count: Option[Int] = None,
  var families: Option[Set[String]] = None) {

  var names: Option[Set[String]] = None

  def process(color: Color) {
    for (name <- color.name) {
      if (names.isDefined) {
        names = Some(names.get + name.toLowerCase)
      } else {
        names = Some(Set(name.toLowerCase))
      }
      count = Some(names.get.size)
    }
    for (family <- color.family) {
      if (families.isDefined) {
        families = Some(families.get + family.toLowerCase)
      } else {
        families = Some(Set(family.toLowerCase))
      }
    }
  }
}

private object ColorSummary {
  implicit val readsDiscountSummary = Json.reads[ColorSummary]
  implicit val writesDiscountSummary = Json.writes[ColorSummary]
}

case class ProductSummary(
  var listPrice: Option[PriceSummary] = None,
  var salePrice: Option[PriceSummary] = None,
  var discountPercent: Option[DiscountSummary] = None,
  var color: Option[ColorSummary] = None) {

  private def processPrice(price: BigDecimal, priceSummary: Option[PriceSummary]) = priceSummary match {
    case Some(summary) => summary.process(price); priceSummary
    case None => Some(new PriceSummary(price, price))
  }

  private def processDiscount(discount: Int, discountSummary: Option[DiscountSummary]) = discountSummary match {
    case Some(summary) => summary.process(discount); discountSummary
    case None => Some(new DiscountSummary(discount, discount))
  }

  private def processColor(color: Color, colorSummary: Option[ColorSummary]) = colorSummary match {
    case Some(summary) =>
      summary.process(color)
      colorSummary
    case None =>
      val summary = new ColorSummary()
      summary.process(color)
      Some(summary)
  }

  def process(sku: Sku) = {
    var processed = false
    for (price <- sku.listPrice) {
      listPrice = processPrice(price, listPrice)
      processed = true
    }
    for (price <- sku.salePrice) {
      salePrice = processPrice(price, salePrice)
      processed = true
    }
    for (discount <- sku.discountPercent) {
      discountPercent = processDiscount(discount, discountPercent)
      processed = true
    }
    for (c <- sku.color) {
      color = processColor(c, color)
      processed = true
    }
    processed
  }
}

object ProductSummary {
  implicit val readsProductSummary = Json.reads[ProductSummary]
  implicit val writesProductSummary = Json.writes[ProductSummary]
}
