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

import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.JavaConversions._

import org.apache.solr.client.solrj.beans.Field
import org.apache.solr.common.SolrInputDocument
import org.apache.commons.lang3.StringUtils
import org.opencommercesearch.api.service.CategoryService

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
  var categories: Option[Seq[String]],
  var skus: Option[Seq[Sku]])
{
  def this() = this(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  @Field
  def setId(id: String) : Unit = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) : Unit = { this.title = Option.apply(title) }
  
  @Field
  def setDescription(description: String) : Unit = { this.description = Option.apply(description) }
  
  @Field
  def setShortDescription(shortDescription: String) : Unit = { this.shortDescription = Option.apply(shortDescription) }    

  @Field
  def setBrandId(id: String) : Unit = {
    if (brand.isEmpty) {
        brand = Option.apply(new Brand())
    }
    brand.get.setId(id) 
  }  
  
  @Field
  def setBrand(name: String) : Unit = {
    if (brand.isEmpty) {
        brand = Option.apply(new Brand())
    }
    brand.get.setName(name) 
  }  

  @Field
  def setGender(gender: String) : Unit = { this.gender = Option.apply(gender) }
  
  @Field
  def setSizingChart(sizingChart: String) : Unit = { this.sizingChart = Option.apply(sizingChart) } 
  
  @Field
  def setBulletPoints(bulletPoints: util.List[String]) {
    this.bulletPoints = Some(JIterableWrapper(bulletPoints).toSeq)
  }   
  
  @Field
  def setDetailImages(detailImages: util.List[String]) {
    this.detailImages = Some(JIterableWrapper(detailImages).map( { image => 
      val parts = StringUtils.split(image, FieldSeparator)
      new Image(Some(parts(0)), Some(parts(1)))    
    }).toSeq)
  }   

  @Field
  def setSkus(skus: Seq[Sku]) : Unit = { this.skus = Option.apply(skus) }
  
  @Field("isOutOfStock")
  def setOutOfStock(isOutOfStock: Boolean) {
    this.isOutOfStock = Option.apply(isOutOfStock)
  }  
  
  @Field
  def setFeatures(features: util.List[String]) {
    this.features = Some(JIterableWrapper(features).map( { feature => 
      val parts = StringUtils.split(feature, FieldSeparator)
      new Attribute(Some(parts(0)), Some(parts(1)))    
    }).toSeq)
  }  
  
  @Field
  def setAttributes(attributes: util.List[String]) {
    this.features = Some(JIterableWrapper(attributes).map( { attribute => 
      val parts = StringUtils.split(attribute, FieldSeparator)
      new Attribute(Some(parts(0)), Some(parts(1)))    
    }).toSeq)
  }
  
  @Field("reviews")
  def setReviewCount(reviewCount: Int) : Unit = { this.reviewCount = Option.apply(reviewCount) }

  @Field("reviewAverage")
  def setReviewAverage(reviewAverage: Float) : Unit = { this.reviewAverage = Option.apply(reviewAverage) }

  @Field("bayesianReviewAverage")
  def setBayesianReviewAverage(bayesianReviewAverage: Float) : Unit = { this.bayesianReviewAverage = Option.apply(bayesianReviewAverage) }
  
  @Field
  def sethasFreeGift(freeGifts: util.List[String]) : Unit = {
    this.hasFreeGift = Some(JIterableWrapper(freeGifts).map( { freeGift => 
      val parts = StringUtils.split(freeGift, FieldSeparator)
      (parts(0), "true".equals(parts(1)))    
    }).toMap)
  }
          
}

object Product {

  implicit val readsProduct = Json.reads[Product]
  implicit val writesProduct = Json.writes[Product]
}

case class ProductList(products: Seq[Product], feedTimestamp: Long) {
  def toDocuments(service: CategoryService, preview: Boolean) : (util.List[SolrInputDocument], util.List[SolrInputDocument]) = {
    val productDocuments = new util.ArrayList[SolrInputDocument](products.size)
    val skuDocuments = new util.ArrayList[SolrInputDocument](products.size * 3)

    var expectedDocCount = 0
    var currentDocCount = 0

    for (product: Product <- products) {
      for (productId <- product.id; title <- product.title; brand <- product.brand; isOutOfStock <- product.isOutOfStock;
           skus <- product.skus; listRank <- product.listRank) {
        expectedDocCount += skus.size
        val productDoc = new SolrInputDocument()
        var gender: String = null
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
        for (bulletPoints <- product.bulletPoints) {
          for (bulletPoint <- bulletPoints) { productDoc.addField("bulletPoints", bulletPoint)}
        }
        for (features <- product.features) { setAttributes("features", productDoc, features) }
        for (attributes <- product.attributes) { setAttributes("attributes", productDoc, attributes) }
        for (reviewAverage <- product.reviewAverage; reviews <- product.reviewCount) {
          productDoc.setField("reviews", reviews)
          productDoc.setField("reviewAverage", reviewAverage)
        }
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
          hasFreeGift.foreach { case(catalog, hasGift) => 
            if (hasGift) {
              productDoc.addField("hasFreeGift", s"$catalog$FieldSeparator$hasGift")
            } 
          } 
        }        
        productDocuments.add(productDoc)

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
            if (gender != null) { doc.setField("gender", gender ) }

            for (year <- sku.year) { doc.setField("year", year) }
            for (season <- sku.season) { doc.setField("season", season) }
            for (colorFamily <- sku.colorFamily) { doc.setField("colorFamily", colorFamily) }
            for (catalogs <- sku.catalogs) { service.loadCategoryPaths(doc, product, catalogs, preview) }

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
                for (onSale <- country.onSale) { doc.setField("onsale" + code, onSale) }
                for (stockLevel <- country.stockLevel)  { doc.setField("stockLevel" + code, stockLevel) }
                for (url <- country.url) { doc.setField("url" + code, url) }
              }
            }

            for (hasFreeGift <- product.hasFreeGift) { 
              hasFreeGift.foreach { case(catalog, hasGift) => 
                if (hasGift) {
                  doc.setField("freeGift" + catalog, hasGift)
                } 
              } 
            }
            for (features <- product.features) { setAttributes(doc, features, FeatureFieldNamePrefix) }
            for (attributes <- product.attributes) { setAttributes(doc, attributes, AttrFieldNamePrefix) }
            for (sort <- sku.customSort) { doc.setField("sort", sort) }
            doc.setField("indexStamp", feedTimestamp)
            skuDocuments.add(doc)
            currentDocCount += 1
          }
        }
      }

      if (expectedDocCount != currentDocCount) {
        throw new IllegalArgumentException("Missing required fields for product " + product.id.get)
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
