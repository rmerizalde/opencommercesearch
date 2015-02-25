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

import org.opencommercesearch.api.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

/**
 * @author nkumar
 */
case class Generation (
  var number: Option[Int],
  var master: Option[Product]) {
}

object Generation {
  implicit val readsGenerationInfo : Reads[Generation] = (
     (__ \ "number").readNullable[Int] ~
     (__ \ "master").lazyReadNullable(Reads.of[Product])
  ) (Generation.apply _)

  implicit val writesGenerationInfo : Writes[Generation] = (
    (__ \ "number").writeNullable[Int] ~
    (__ \ "master").lazyWriteNullable(Writes.of[Product])
  ) (unlift(Generation.unapply))


  implicit object GenerationWriter extends BSONDocumentWriter[Generation] {
    import reactivemongo.bson._
    def write(generation: Generation): BSONDocument = BSONDocument(
      "number" -> generation.number,
      "master" -> generation.master
    )
  }

  implicit object GenerationReader extends BSONDocumentReader[Generation] {
    def read(doc: BSONDocument): Generation = Generation(
      doc.getAs[Int]("number"),
      doc.getAs[Product]("master")
    )
  }
}