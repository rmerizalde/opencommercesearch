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

import org.opencommercesearch.api.util.{BigDecimalDeserializer, BigDecimalSerializer}

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}

case class Country(
  @JsonProperty("code") var code: Option[String] = None,
  @JsonProperty("listPrice")
  @JsonSerialize(using = classOf[BigDecimalSerializer])
  @JsonDeserialize(using = classOf[BigDecimalDeserializer])
  var listPrice: Option[BigDecimal] = None,
  @JsonProperty("salePrice")
  @JsonSerialize(using = classOf[BigDecimalSerializer])
  @JsonDeserialize(using = classOf[BigDecimalDeserializer])
  var salePrice: Option[BigDecimal] = None,
  @JsonProperty("discountPercent") var discountPercent: Option[Int] = None,
  @JsonProperty("onSale") var onSale: Option[Boolean] = None,
  @JsonProperty("stockLevel") var stockLevel: Option[Int] = None,
  @JsonProperty("allowBackorder") var allowBackorder: Option[Boolean] = None,
  @JsonProperty("url") var url: Option[String] = None,
  @JsonProperty("availability") var availability: Option[Availability] = None) {

}


object Country {
  implicit val readsCountry = Json.reads[Country]
  implicit val writesCountry = Json.writes[Country]
}
