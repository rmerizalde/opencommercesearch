package org.opencommercesearch.api.models

import play.api.libs.json.Json
import com.fasterxml.jackson.annotation.{JsonProperty, JsonCreator}

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
  @JsonProperty("code") var code: Option[String],
  //TODO gsegura: there were issues deserializing bigdecimals, even with the @JsonDeserialize annotation
  @JsonProperty("listPrice") var listPrice: Option[Double],
  @JsonProperty("salePrice") var salePrice: Option[Double],
  @JsonProperty("discountPercent") var discountPercent: Option[Int],
  @JsonProperty("onSale") var onSale: Option[Boolean],
  @JsonProperty("stockLevel") var stockLevel: Option[Int],
  @JsonProperty("url") var url: Option[String],
  @JsonProperty("allowBackorder") var allowBackorder: Option[Boolean]) {

  @JsonCreator
  def this() = this(None, None, None, None, None, None, None, None)

}


object Country {
  implicit val readsCountry = Json.reads[Country]
  implicit val writesCountry = Json.writes[Country]
}
