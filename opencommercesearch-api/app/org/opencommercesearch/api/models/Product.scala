
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
import java.util.Date

import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.suggester.IndexableElement

import org.apache.commons.lang3.StringUtils
import org.apache.solr.common.SolrInputDocument

import org.jongo.marshall.jackson.oid.Id

import ProductList._
import play.api.Logger
import org.opencommercesearch.api.Global.IndexOemProductsEnabled
import com.fasterxml.jackson.annotation.JsonProperty

object Product {
  def getInstance() =  new Product(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  implicit val readsProduct = Json.reads[Product]
  implicit val writesProduct = Json.writes[Product]
}


case class Product (
  @Id var id: Option[String],
  @JsonProperty("title") var title: Option[String],
  @JsonProperty("description") var description: Option[String],
  @JsonProperty("shortDescription") var shortDescription: Option[String],
  @JsonProperty("brand") var brand: Option[Brand],
  @JsonProperty("gender") var gender: Option[String],
  @JsonProperty("sizingChart") var sizingChart: Option[String],
  @JsonProperty("detailImages") var detailImages: Option[Seq[Image]],
  @JsonProperty("bulletPoints") var bulletPoints: Option[Seq[String]],
  @JsonProperty("attributes") var attributes: Option[Seq[Attribute]],
  @JsonProperty("features") var features: Option[Seq[Attribute]],
  @JsonProperty("listRank") var listRank: Option[Int],
  @JsonProperty("customerReviews") var customerReviews: Option[CustomerReview],
  @JsonProperty("hasFreeGift") var hasFreeGift: Option[Map[String, Boolean]],
  @JsonProperty("isOutOfStock") var isOutOfStock: Option[Boolean],
  @JsonProperty("categories") var categories: Option[Seq[Category]],
  @JsonProperty("skus") var skus: Option[Seq[Sku]],
  @JsonProperty("activationDate") var activationDate: Option[Date],
  @JsonProperty("isPackage") var isPackage: Option[Boolean],
  @JsonProperty("isOem") var isOem: Option[Boolean]) extends IndexableElement
{
  def getId : String = { this.id.get }

  override def source = "product"

  override def toJson : JsValue = { Json.toJson(this) }
  
  def getNgramText : String = {
    this.title.getOrElse(StringUtils.EMPTY)
  }

  def getType : String = {
    "product"
  }

  def getSites : Seq[String] = {
    val skus = this.skus.getOrElse(Seq.empty[Sku])
    skus.foldRight(Set.empty[String]) {
      (iterable, accum) =>
        accum ++ iterable.catalogs.getOrElse(Seq.empty[String])
    }.toSeq
  }
}

case class ProductList(products: Seq[Product], feedTimestamp: Long) {

  def toDocuments(service: CategoryService)(implicit context: Context) : (util.List[SolrInputDocument], util.List[SolrInputDocument]) = {
    val productDocuments = new util.ArrayList[SolrInputDocument](products.size)
    val skuDocuments = new util.ArrayList[SolrInputDocument](products.size * 3)

    var expectedDocCount = 0
    var currentDocCount = 0

    for (product: Product <- products) {
      for (productId <- product.id; title <- product.title; brand <- product.brand; isOutOfStock <- product.isOutOfStock;
           skus <- product.skus; listRank <- product.listRank) {
        val isOem = product.isOem.getOrElse(false)
        if (IndexOemProductsEnabled || !isOem) {
          expectedDocCount += skus.size
          val productDoc = new SolrInputDocument()
          var gender: String = null
          var activationDate: Date = null
          productDoc.setField("id", productId)
          productDoc.setField("title", title)
          for (brandId <- brand.id) {
            productDoc.setField("brand", brandId)
          }
          productDoc.setField("isOutOfStock", isOutOfStock)

          for (description <- product.description; shortDescription <- product.shortDescription) {
            productDoc.setField("description", description)
            productDoc.setField("shortDescription", shortDescription)
          }
          for (g <- product.gender) { productDoc.setField("gender", gender = g) }
          for (activeDate <- product.activationDate) { activationDate = activeDate }
          for (bulletPoints <- product.bulletPoints) {
            for (bulletPoint <- bulletPoints) { productDoc.addField("bulletPoints", bulletPoint) }
          }
          for (features <- product.features) { setAttributes("features", productDoc, features) }
          for (attributes <- product.attributes) { setAttributes("attributes", productDoc, attributes) }

          for (sizingChart <- product.sizingChart) { productDoc.setField("sizingChart", sizingChart) }
          for (detailImages <- product.detailImages) {
            for (detailImage <- detailImages) {
              var title = ""
              var url = ""
              for (t <- detailImage.title) { title = t }
              for (u <- detailImage.url) { url = u }
              productDoc.addField("detailImages", title + FieldSeparator + url)
            }
          }
          for (hasFreeGift <- product.hasFreeGift) {
            hasFreeGift.foreach { case (catalog, hasGift) =>
                if (hasGift) {
                  productDoc.addField("hasFreeGift", s"$catalog$FieldSeparator$hasGift")
                }
            }
          }
          productDocuments.add(productDoc)
          val skuCount = skus.size
          var skuSort = 0
          for (sku: Sku <- skus) {
            for (id <- sku.id; image <- sku.image; isRetail <- sku.isRetail;
                 isCloseout <- sku.isCloseout; countries <- sku.countries) {
              val doc = new SolrInputDocument()

              if (isOutOfStock) {
                //for outOfStock scenario we need to save the id of the document with the "-toos" suffix
                //and the productId should be the regular id, without the suffix.
                doc.setField("id", id + "-toos")
                doc.setField("productId", productId)
              } else {
                doc.setField("id", id)
                doc.setField("productId", productId)
              }
              doc.setField("title", title)
              for (brandId <- brand.id) { doc.setField("brandId", brandId) }
              for (brandName <- brand.name) { doc.setField("brand", brandName) }
              for (imageUrl <- image.url) { doc.setField("image", imageUrl) }
              doc.setField("listRank", listRank)
              doc.setField("isToos", isOutOfStock)
              doc.setField("isRetail", isRetail)
              doc.setField("skuCount", skuCount)
              doc.setField("isCloseout", isCloseout)
              for (isOutlet <- sku.isOutlet) { doc.setField("isOutlet", isOutlet) }
              for (isPastSeason <- sku.isPastSeason) { doc.setField("isPastSeason", isPastSeason) }
              if (gender != null) { doc.setField("gender", gender) }
              if (activationDate != null) { doc.setField("activationDate", activationDate) }

              for (year <- sku.year) { doc.setField("year", year) }
              for (season <- sku.season) { doc.setField("season", season) }

              for (color <- sku.color) {
                for (name <- color.name; family <- color.family) {
                  doc.setField("colorFamily", family)
                  doc.setField("color", name)
                }
              }

              for (catalogs <- sku.catalogs) { service.loadCategoryPaths(doc, product, catalogs) }

              for (size <- sku.size) {
                for (sizeName <- size.name) { doc.setField("size", sizeName) }
                for (scale <- size.scale) { doc.setField("scale", scale) }
              }

              for (reviews <- product.customerReviews) {
                doc.setField("reviews", reviews.count)
                doc.setField("reviewAverage", reviews.average)
                doc.setField("bayesianReviewAverage", reviews.bayesianAverage)
              }

              for (country: Country <- countries) {
                for (code <- country.code) {
                  doc.addField("country", code)

                  for (allowBackorder <- country.allowBackorder) { doc.setField("allowBackorder" + code, allowBackorder) }
                  for (listPrice <- country.listPrice) { doc.setField("listPrice" + code, listPrice) }

                  for (salePrice <- country.salePrice) { doc.setField("salePrice" + code, salePrice) }
                  for (discountPercent <- country.discountPercent) { doc.setField("discountPercent" + code, discountPercent) }
                  for (onSale <- country.onSale) { doc.setField("onsale" + code, onSale) }
                  for (stockLevel <- country.stockLevel) { doc.setField("stockLevel" + code, stockLevel) }
                  for (url <- country.url) { doc.setField("url" + code, url) }
                }
              }

              for (hasFreeGift <- product.hasFreeGift) {
                hasFreeGift.foreach { case (catalog, hasGift) =>
                    if (hasGift) {
                      doc.setField("freeGift" + catalog, hasGift)
                    }
                }
              }
              for (features <- product.features) { setAttributes(doc, features, FeatureFieldNamePrefix) }
              for (attributes <- product.attributes) { setAttributes(doc, attributes, AttrFieldNamePrefix) }
              doc.setField("sort", skuSort)
              skuSort += 1
              doc.setField("indexStamp", feedTimestamp)
              skuDocuments.add(doc)
              currentDocCount += 1
            }
          }
        } else {
          Logger.info(s"Product $productId is original equipment manufacturer and won't be index in search")
        }

        if (expectedDocCount != currentDocCount) {
          throw new IllegalArgumentException("Missing required fields for product " + product.id.get)
        }
      }
    }
    (productDocuments, skuDocuments)
  }

  def setAttributes(fieldName: String, doc: SolrInputDocument, features: Seq[Attribute]) : Unit = {
    for (feature <- features) {
      for (name <- feature.name; value <- feature.value) {
        doc.addField(fieldName, name + FieldSeparator + value)
      }
    }  
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
  val FieldSeparator = "#--@--#"
  val FreeGift = "freeGift"

  implicit val readsProductList = Json.reads[ProductList]
  implicit val writesProductList = Json.writes[ProductList]
}
