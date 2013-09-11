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

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.util

import org.apache.solr.client.solrj.beans.Field

case class Product (var id: Option[String], var title: Option[String], var skus: Option[Seq[Sku]]) {
  def this() = this(null, null, null)

  @Field
  def setId(id: String) : Unit = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) : Unit = { this.title = Option.apply(title) }

  @Field
  def setSkus(skus: Seq[Sku]) : Unit = { this.skus = Option.apply(skus) }
}

object Product {

  implicit val readsProduct = Json.reads[Product]
  implicit val writesProduct = Json.writes[Product]

  implicit val objectSeqFormat = new Format[Seq[Sku]] {
    def writes(seq: Seq[Sku]): JsValue = {
      Json.arr(
        seq.map(s => Json.toJson(s))
      )
    }

    def reads(jv: JsValue): JsResult[Seq[Sku]] =
      JsSuccess(jv.as[JsArray].value.map(_.as[Sku]))
  }

}
