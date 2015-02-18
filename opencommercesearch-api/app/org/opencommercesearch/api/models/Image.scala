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
 * A image model
 * @param title is the title for this image
 * @param url is the url of the image
 *
 * @author rmerizalde
 */
case class Image(
  var title: Option[String],
  var url: Option[String]) {
}

object Image {
  implicit val readsImage = Json.reads[Image]
  implicit val writesImage = Json.writes[Image]

  implicit object ImageWriter extends BSONDocumentWriter[Image] {
    import reactivemongo.bson._

    def write(image: Image): BSONDocument = BSONDocument(
      "title" -> image.title,
      "url" -> image.url
    )
  }

  implicit object ImageReader extends BSONDocumentReader[Image] {
    def read(doc: BSONDocument): Image = Image(
      doc.getAs[String]("title"),
      doc.getAs[String]("url")
    )
  }
}
