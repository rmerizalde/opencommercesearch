package org.opencommercesearch.api.models

import play.api.libs.json.Json
import com.fasterxml.jackson.annotation.{JsonProperty, JsonCreator}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.opencommercesearch.api.BigDecimalDeserializer

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

case class Country(
  var code: Option[String],
  var listPrice: Option[BigDecimal],
  var salePrice: Option[BigDecimal],
  var discountPercent: Option[Int],
  var onSale: Option[Boolean],
  var stockLevel: Option[Int],
  var url: Option[String],
  var allowBackorder: Option[Boolean]) {

  @JsonCreator
  def this() = this(None, None, None, None, None, None, None, None)

  @JsonProperty("listPrice")
  @JsonDeserialize(using = classOf[BigDecimalDeserializer])
  def setListPrice(listPrice: BigDecimal) : Unit = {
    this.listPrice = Option.apply(listPrice)
  }

  @JsonProperty("salePrice")
  @JsonDeserialize(using = classOf[BigDecimalDeserializer])
  def setSalePrice(salePrice: BigDecimal) : Unit = {
    this.salePrice = Option.apply(salePrice)
  }
}


object Country {
  implicit val readsCountry = Json.reads[Country]
  implicit val writesCountry = Json.writes[Country]
}
