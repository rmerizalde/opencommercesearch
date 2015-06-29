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
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

case class Country(
  var code: Option[String] = None,
  @deprecated("Use defaultPrice")
  var listPrice: Option[BigDecimal] = None,
  @deprecated("Use defaultPrice")
  var salePrice: Option[BigDecimal] = None,
  @deprecated("Use defaultPrice")
  var discountPercent: Option[Int] = None,
  var defaultPrice: Option[Price] = None,
  var catalogPrices: Option[Map[String, Price]] = None,
  var onSale: Option[Boolean] = None,
  var stockLevel: Option[Int] = None,
  var allowBackorder: Option[Boolean] = None,
  var url: Option[String] = None,
  var availability: Option[Availability] = None) {

  def isPoos: Boolean = availability match {
    case Some(avail) => avail.status.orNull == Availability.PermanentlyOutOfStock
    case None => false
  }
}


object Country {
  implicit val readsCountry = Json.reads[Country]
  implicit val writesCountry = Json.writes[Country]

  implicit object CountryWriter extends BSONDocumentWriter[Country] {
    import org.opencommercesearch.bson.BSONFormats._

    def write(country: Country): BSONDocument = BSONDocument(
      "code" -> country.code,
      "listPrice" -> country.listPrice,
      "salePrice" -> country.salePrice,
      "discountPercent" -> country.discountPercent,
      "onSale" -> country.onSale,
      "allowBackorder" -> country.allowBackorder,
      "url" -> country.url,
      "availability" -> country.availability,
      "defaultPrice" -> country.defaultPrice,
      "catalogPrices" -> country.catalogPrices

    )
  }

  implicit object CountryReader extends BSONDocumentReader[Country] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): Country = Country(
      code = doc.getAs[String]("code"),
      listPrice = doc.getAs[BigDecimal]("listPrice"),
      salePrice = doc.getAs[BigDecimal]("salePrice"),
      discountPercent = doc.getAs[Int]("discountPercent"),
      onSale = doc.getAs[Boolean]("onSale"),
      allowBackorder = doc.getAs[Boolean]("allowBackorder"),
      url = doc.getAs[String]("url"),
      availability = doc.getAs[Availability]("availability"),
      defaultPrice = doc.getAs[Price]("defaultPrice"),
      catalogPrices = doc.getAs[Map[String, Price]]("catalogPrices")
    )
  }
}
