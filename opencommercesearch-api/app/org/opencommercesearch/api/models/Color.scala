package org.opencommercesearch.api.models

import play.api.libs.json.Json
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

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

case class Color(
  var name: Option[String],
  var family: Option[String]) {
}

object Color {
  implicit val readsColorInfo = Json.reads[Color]
  implicit val writesColorInfo = Json.writes[Color]

  implicit object ColorWriter extends BSONDocumentWriter[Color] {
    def write(color: Color): BSONDocument = BSONDocument(
      "name" -> color.name,
      "family" -> color.family
    )
  }

  implicit object ColorReader extends BSONDocumentReader[Color] {
    def read(doc: BSONDocument): Color = Color(
      doc.getAs[String]("name"),
      doc.getAs[String]("family")
    )
  }
}
