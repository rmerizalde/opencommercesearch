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

case class ProductBoost(id: String, value: BigDecimal) {
}

object ProductBoost {
  implicit val readsProductBoost = Json.reads[ProductBoost]
  implicit val writesProductBoost = Json.writes[ProductBoost]

  implicit object BoostWriter extends BSONDocumentWriter[ProductBoost] {
    import reactivemongo.bson._
    import org.opencommercesearch.bson.BSONFormats._

    def write(boost: ProductBoost): BSONDocument = BSONDocument(
      "id" -> boost.id,
      "value" -> boost.value
    )
  }

  implicit object BoostReader extends BSONDocumentReader[ProductBoost] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): ProductBoost = ProductBoost(
      doc.getAs[String]("id").orNull,
      doc.getAs[BigDecimal]("value").getOrElse(1)
    )
  }
}

case class PageBoost(id: String, feedTimestamp: Long, boosts: Option[Seq[ProductBoost]]) {
}

object PageBoost {
  implicit val readsPageBoost = Json.reads[PageBoost]
  implicit val writesPageBoost = Json.writes[PageBoost]

  implicit object PageBoostWriter extends BSONDocumentWriter[PageBoost] {
    import reactivemongo.bson._

    def write(pageBoost: PageBoost): BSONDocument = BSONDocument(
      "_id" -> pageBoost.id,
      "feedTimestamp" -> pageBoost.feedTimestamp,
      "boosts" -> pageBoost.boosts
    )
  }

  implicit object PageBoostReader extends BSONDocumentReader[PageBoost] {

    def read(doc: BSONDocument): PageBoost = PageBoost(
      doc.getAs[String]("_id").orNull,
      doc.getAs[Long]("feedTimestamp").getOrElse(0),
      doc.getAs[Seq[ProductBoost]]("boosts")
    )
  }
}




