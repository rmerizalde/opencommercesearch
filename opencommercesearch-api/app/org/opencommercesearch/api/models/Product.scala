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

import java.util

import org.apache.solr.client.solrj.beans.Field
import org.apache.solr.common.SolrInputDocument

import ProductList._

case class Product (
  var id: Option[String],
  var title: Option[String],
  var description: Option[String],
  var shortDescription: Option[String],
  var brand: Option[Brand],
  var gender: Option[String],
  var sizingChart: Option[String],
  var detailImages: Option[Seq[Image]],
  var bulletPoints: Option[Seq[String]],
  var attributes: Option[Seq[Attribute]],
  var features: Option[Seq[Attribute]],
  var listRank: Option[Int],
  var reviewCount: Option[Int],
  var reviewAverage: Option[Float],
  var bayesianReviewAverage: Option[Float],
  // has free gift by catalog
  var hasFreeGift: Option[Map[String, Boolean]],
  var isOutOfStock: Option[Boolean],
  var skus: Option[Seq[Sku]])
{
  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

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
}

case class ProductList(products: Seq[Product], indexTimestamp: Long) {
  def toDocuments() : util.List[SolrInputDocument] = {
    val documents = new util.ArrayList[SolrInputDocument](products.size * 3)
    var expectedDocCount = 0
    var currentDocCount = 0

    for (product: Product <- products) {
      for (productId <- product.id; title <- product.title; brand <- product.brand; isOutOfStock <- product.isOutOfStock;
           skus <- product.skus; listRank <- product.listRank) {
        expectedDocCount += skus.size
        for (sku: Sku <- skus) {
          for (id <- sku.id; image <- sku.image; isRetail <- sku.isRetail;
               isCloseout <- sku.isCloseout; countries <- sku.countries) {
            val doc = new SolrInputDocument()
            doc.setField("id", id)
            doc.setField("productId", productId)
            doc.setField("title", title)
            for (brandId <- brand.id) { doc.setField("brandId", brandId) }
            for (brandName <- brand.name) { doc.setField("brand", brandName) }
            for (imageUrl <- image.url) { doc.setField("image", imageUrl) }
            doc.setField("listRank", listRank)
            doc.setField("isToos", isOutOfStock)
            doc.setField("isRetail", isRetail)
            doc.setField("isCloseout", isCloseout)
            for (isPastSeason <- sku.isPastSeason) { doc.setField("isPastSeason", isPastSeason) }
            for (gender <- product.gender) { doc.setField("gender", gender) }
            for (year <- sku.year) { doc.setField("year", year) }
            for (season <- sku.season) { doc.setField("season", season) }
            for (colorFamily <- sku.colorFamily) { doc.setField("colorFamily", colorFamily) }

            for (size <- sku.size) {
              for (sizeName <- size.name) { doc.setField("size", sizeName) }
              for (scale <- size.scale) { doc.setField("scale", scale) }
            }

            for (reviewAverage <- product.reviewAverage; bayesianReviewAverage <- product.bayesianReviewAverage;
                 reviews <- product.reviewCount) {
              doc.setField("reviews", reviews)
              doc.setField("reviewAverage", reviewAverage)
              doc.setField("bayesianReviewAverage", bayesianReviewAverage)
            }

            for (country: Country <- countries) {
              for (code <- country.code) {
                doc.addField("country", code)

                for (allowBackorder <- country.allowBackorder) { doc.setField("allowBackorder" + code, allowBackorder) }
                for (listPrice <- country.listPrice) { doc.setField("listPrice" + code, listPrice) }
                for (salePrice <- country.salePrice) { doc.setField("salePrice" + code, salePrice) }
                for (discountPercent <- country.discountPercent) { doc.setField("discountPercent" + code, discountPercent) }
                for (onSale <- country.onSale) { doc.setField("onSale" + code, onSale) }
                for (stockLevel <- country.stockLevel)  { doc.setField("stockLevel" + code, stockLevel) }
                for (url <- country.url) { doc.setField("url" + code, url) }
              }
            }

            for (features <- product.features) { setAttributes(doc, features, FeatureFieldNamePrefix) }
            for (attributes <- product.attributes) { setAttributes(doc, attributes, AttrFieldNamePrefix) }
            for (sort <- sku.customSort) { doc.setField("sort", sort) }

            doc.setField("category", "categoryTBD")
            doc.setField("categoryPath", "categoryPathTBD")
            doc.setField("ancestorCategoryId", "ancestorCategoryIdTBD")
            doc.setField("category", "categoryTBD")
            doc.setField("categoryNodes", "categoryNodesTBD")
            doc.setField("categoryLeaves", "categoryLeavesTBD")
            doc.setField("indexStamp", indexTimestamp)
            documents.add(doc)
            currentDocCount += 1
          }
        }
      }

      if (expectedDocCount != currentDocCount) {
        println(expectedDocCount + " != " + currentDocCount)

        throw new IllegalArgumentException("Missing required fields for product " + product.id.get)
      }
    }
    documents
  }

  def setAttributes(doc: SolrInputDocument, features: Seq[Attribute], prefix: String) : Unit = {
    for (feature <- features) {
      for (name <- feature.name; value <- feature.value) {
        doc.addField(prefix + name.toLowerCase.replaceAll(AttrNameCleanupPattern, ""), value)
      }
    }
  }

}

object ProductList {
  val AttrNameCleanupPattern = "[^a-z]"
  val AttrFieldNamePrefix = "attr_"
  val FeatureFieldNamePrefix = "feature_"

  implicit val readsProductList = Json.reads[ProductList]
  implicit val writesProductList = Json.writes[ProductList]
}
