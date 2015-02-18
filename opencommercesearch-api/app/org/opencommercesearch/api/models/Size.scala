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
case class Size(
    var name: Option[String] = None,
    var scale: Option[String] = None,
    var preferred: Option[Size] = None) {

}

object Size {
  implicit val readsSize = Json.reads[Size]
  implicit val writesSize = Json.writes[Size]

  implicit object SizeWriter extends BSONDocumentWriter[Size] {
    def write(size: Size): BSONDocument = BSONDocument(
      "name" -> size.name,
      "scale" -> size.scale,
      "preferred" -> size.preferred
    )
  }

  implicit object SizeReader extends BSONDocumentReader[Size] {
    def read(doc: BSONDocument): Size = Size(
      doc.getAs[String]("name"),
      doc.getAs[String]("scale"),
      doc.getAs[Size]("preferred")
    )
  }
}
