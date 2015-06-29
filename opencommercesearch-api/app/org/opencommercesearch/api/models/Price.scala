package org.opencommercesearch.api.models

import play.api.libs.json.Json
import reactivemongo.bson.{BSONDocumentReader, BSONDocument, BSONDocumentWriter}

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

/**
 *
 * @param listPrice is the list price
 * @param salePrice is the sale price
 * @param discountPercent is the discount
 */
case class Price(
  var listPrice: Option[BigDecimal] = None,
  var salePrice: Option[BigDecimal] = None,
  var discountPercent: Option[Int] = None,
  var onSale: Option[Boolean] = None)

object Price {
  implicit val readsPrice = Json.reads[Price]
  implicit val writesPrice = Json.writes[Price]

  implicit object PriceWriter extends BSONDocumentWriter[Price] {
    import org.opencommercesearch.bson.BSONFormats._

    def write(price: Price): BSONDocument = BSONDocument(
      "listPrice" -> price.listPrice,
      "salePrice" -> price.salePrice,
      "discountPercent" -> price.discountPercent,
      "onSale" -> price.onSale
    )
  }

  implicit object PriceReader extends BSONDocumentReader[Price] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): Price = Price(
      listPrice = doc.getAs[BigDecimal]("listPrice"),
      salePrice = doc.getAs[BigDecimal]("salePrice"),
      discountPercent = doc.getAs[Int]("discountPercent"),
      onSale = doc.getAs[Boolean]("onSale")
    )
  }
}
