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

import play.api.libs.json.{Json}

import scala.collection.JavaConversions._
import scala.collection.mutable.Map

import java.util

import org.apache.solr.client.solrj.beans.Field

import Sku._
import org.apache.commons.lang3.StringUtils

case class Sku(
  var id: Option[String],
  var season: Option[String],
  var year: Option[String],
  var image: Option[Image],
  var countries: Option[Seq[Country]],
  var isPastSeason: Option[Boolean],
  var colorFamily: Option[Array[String]],
  var isRetail: Option[Boolean],
  var isCloseout: Option[Boolean],
  var isOutlet: Option[Boolean],
  var size: Option[Size],
  var catalogs: Option[Seq[String]],
  var customSort: Option[Int]) {

  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None)

  @Field
  def setId(id: String) : Unit = { this.id = Option.apply(id) }

  @Field
  def setImage(url: String) : Unit = {
    this.image = Option.apply(new Image(None, Option.apply(url)))
  }


  @Field("listPrice*")
  def setListPrice(listPrices: util.Map[String, Float]) : Unit = {
    val priceMap: Map[String, Float] = listPrices
    ensureCountries(ListPrice, listPrices)
    for (countries <- this.countries) {
      for (country <- countries; code <- country.code) {
        country.listPrice = priceMap.get(ListPrice + code)
      }
    }
  }

  @Field("salePrice*")
  def setSaletPrice(salePrices: util.Map[String, Float]) : Unit = {
    val priceMap: Map[String, Float] = salePrices
    ensureCountries(SalePrice, salePrices)
    for (countries <- this.countries) {
      for (country <- countries; code <- country.code) {
        country.salePrice = priceMap.get(SalePrice + code)
      }
    }
  }

  @Field("discountPercent*")
  def setDiscountPercent(discountPercents: util.Map[String, Int]) : Unit = {
    val discountMap: Map[String, Int] = discountPercents
    ensureCountries(DiscountPercent, discountPercents)
    for (countries <- this.countries) {
      for (country <- countries; code <- country.code) {
        country.discountPercent = discountMap.get(DiscountPercent + code)
      }
    }
  }

  @Field("stockLevel*")
  def setStockLevel(stockLevels: util.Map[String, Int]) : Unit = {
    val stockMap: Map[String, Int] = stockLevels
    ensureCountries(Stocklevel, stockLevels )
    for (countries <- this.countries) {
      for (country <- countries; code <- country.code) {
        country.discountPercent = stockMap.get(Stocklevel + code)
      }
    }
  }

  @Field("url*")
  def setUrl(urls: util.Map[String, String]) : Unit = {
    val urlMap: Map[String, String] = urls
    ensureCountries(Url, urls)
    for (countries <- this.countries) {
      for (country <- countries; code <- country.code) {
        country.url = urlMap.get(Url + code)
      }
    }
  }

  @Field("isPastSeason")
  def setPastSeason(isPastSeason: Boolean) {
    this.isPastSeason = Option.apply(isPastSeason)
  }

  @Field("colorFamily")
  def setColorFamily(colorFamily: Array[String]) {
    this.colorFamily = Option.apply(colorFamily)
  }
  
  @Field("size")
  def setSize(name: String) : Unit = {
       this.size = Option.apply(new Size(Option.apply(name), None))
  }
  
  @Field("isCloseout")
  def setCloseout(isCloseout: Boolean) {
    this.isCloseout = Option.apply(isCloseout)
  }

  @Field("isOutlet")
  def setOutlet(isOutlet: Boolean) {
    this.isOutlet = Option.apply(isOutlet)
  }

  /**
   * Helper method to ensure the country object for this skus exist.
   * If countries is empty it will create countries based on the suffix
   * of the given dynamic field names.
   *
   * @param fieldName is the dynamic field name. Anything after the field name is considered the country code
   * @param fields is the map between field name and values (e.g. (listPriceUS=>20.0, listPriceCA=>40)
   * @tparam T is the type of the field value
   */
  private def ensureCountries[T](fieldName: String, fields: Map[String, T]) : Unit = {
    if (countries.isEmpty) {
      val codes = fields.map( entry => { entry._1 stripPrefix fieldName } ).to[Seq]
      countries = Some(codes.map( code => {
        new Country(code)
      } ))
    }
  }


}

object Sku {
  val ListPrice = "listPrice"
  val SalePrice = "salePrice"
  val DiscountPercent = "discountPercent"
  val Url = "url"
  val Stocklevel = "stockLevel"

  implicit val readsSku = Json.reads[Sku]
  implicit val writesSku = Json.writes[Sku]
}

