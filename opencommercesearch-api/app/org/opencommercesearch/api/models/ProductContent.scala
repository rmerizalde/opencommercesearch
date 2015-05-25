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

/**
 * A product content model
 * @param description is the description for this product
 * @param bottomline is the bottomline for this product
 *
 * @author rmadrigal
 */
case class ProductContent(
  var id: Option[String] = None,
  var productId: Option[String] = None,
  var site: Option[String] = None,
  var bottomLine: Option[String] = None,
  var description: Option[String] = None,
  var feedTimestamp: Option[Long] = None
)

object ProductContent {
  val BottomLine = "bottomLine"

  implicit val readsProductContent = Json.reads[ProductContent]
  implicit val writesProductContent = Json.writes[ProductContent]

  implicit object ProductContentWriter extends BSONDocumentWriter[ProductContent] {
    import reactivemongo.bson._

    def write(productContent: ProductContent): BSONDocument = BSONDocument(
      "_id" -> productContent.id,
      "productId" -> productContent.productId,
      "site" -> productContent.site,
      "bottomLine" -> productContent.bottomLine,
      "description" -> productContent.description,
      "feedTimestamp" -> productContent.feedTimestamp
    )
  }

  implicit object ProductContentReader extends BSONDocumentReader[ProductContent] {
    def read(doc: BSONDocument): ProductContent = ProductContent(
      doc.getAs[String]("_id"),
      doc.getAs[String]("productId"),
      doc.getAs[String]("site"),
      doc.getAs[String]("bottomLine"),
      doc.getAs[String]("description"),
      doc.getAs[Long]("feedTimestamp")
    )
  }
}

case class ProductContentList(contents: Seq[ProductContent], feedTimestamp: Long)

object ProductContentList {
  implicit val readsProductList = Json.reads[ProductContentList]
  implicit val writesProductList = Json.writes[ProductContentList]
}
