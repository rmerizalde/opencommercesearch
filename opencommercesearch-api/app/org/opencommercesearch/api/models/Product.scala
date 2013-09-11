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

case class Product (
  var id: Option[String],
  var title: Option[String],
  var description: Option[String],
  var shortDescription: Option[String],
  var brand: Option[Brand],
  var sizingChart: Option[String],
  var detailImages: Option[List[Image]],
  var bulletPoints: Option[List[String]],
  var features: Option[List[Attribute]],
  var listRank: Option[Int],
  var reviewCount: Option[Int],
  var reviewAverage: Option[Float],
  var bayesianReviewAverage: Option[Float],
  // has free gift by catalog
  var hasFreeGift: Option[Map[String, Boolean]],
  var isOutOfStock: Option[Boolean],
  var skus: Option[Seq[SolrSku]])
{
  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  @Field
  def setId(id: String) : Unit = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) : Unit = { this.title = Option.apply(title) }

  @Field
  def setSkus(skus: Seq[SolrSku]) : Unit = { this.skus = Option.apply(skus) }
}

object Product {

  implicit val readsProduct = Json.reads[Product]
  implicit val writesProduct = Json.writes[Product]
}

case class ProductList(products: Seq[Product]) {}

object ProductList {
  implicit val readsProductList = Json.reads[ProductList]
  implicit val writesProductList = Json.writes[ProductList]
}
