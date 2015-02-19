
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

import java.util
import java.util.Date

import org.apache.commons.lang3.StringUtils
import org.apache.solr.common.SolrInputDocument
import org.opencommercesearch.api.Global.{IndexOemProductsEnabled, ProductAvailabilityStatusSummary}
import org.opencommercesearch.api.Implicits._
import org.opencommercesearch.api.models.ProductList._
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.suggester.IndexableElement
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

object Product {

  implicit val readsProduct : Reads[Product] = (
    (__ \ "id").readNullable[String] ~
    (__ \ "title").readNullable[String] ~
    (__ \ "description").readNullable[String] ~
    (__ \ "shortDescription").readNullable[String] ~
    (__ \ "brand").readNullable[Brand] ~
    (__ \ "gender").readNullable[String] ~
    (__ \ "sizingChart").readNullable[String] ~
    (__ \ "detailImages").readNullable[Seq[Image]] ~
    (__ \ "bulletPoints").readNullable[Seq[String]] ~
    (__ \ "attributes").readNullable[Seq[Attribute]] ~
    (__ \ "features").readNullable[Seq[Attribute]] ~
    (__ \ "listRank").readNullable[Int] ~
    (__ \ "customerReviews").readNullable[CustomerReview] ~
    (__ \ "freeGifts").lazyReadNullable(Reads.map[Seq[Product]]) ~
    (__ \ "availabilityStatus").readNullable[String] ~
    (__ \ "categories").readNullable[Seq[Category]] ~
    (__ \ "skus").readNullable[Seq[Sku]] ~
    (__ \ "generation").readNullable[Generation] ~
    (__ \ "activationDate").readNullable[Date] ~
    (__ \ "isPackage").readNullable[Boolean] ~
    (__ \ "isOem").readNullable[Boolean]
  ) (Product.apply _)

  implicit val writesProduct : Writes[Product] = (
    (__ \ "id").writeNullable[String] ~
    (__ \ "title").writeNullable[String] ~
    (__ \ "description").writeNullable[String] ~
    (__ \ "shortDescription").writeNullable[String] ~
    (__ \ "brand").writeNullable[Brand] ~
    (__ \ "gender").writeNullable[String] ~
    (__ \ "sizingChart").writeNullable[String] ~
    (__ \ "detailImages").writeNullable[Seq[Image]] ~
    (__ \ "bulletPoints").writeNullable[Seq[String]] ~
    (__ \ "attributes").writeNullable[Seq[Attribute]] ~
    (__ \ "features").writeNullable[Seq[Attribute]] ~
    (__ \ "listRank").writeNullable[Int] ~
    (__ \ "customerReviews").writeNullable[CustomerReview] ~
    (__ \ "freeGifts").lazyWriteNullable(Writes.map[Seq[Product]]) ~
    (__ \ "availabilityStatus").writeNullable[String] ~
    (__ \ "categories").writeNullable[Seq[Category]] ~
    (__ \ "skus").writeNullable[Seq[Sku]] ~
    (__ \ "generation").writeNullable[Generation] ~
    (__ \ "activationDate").writeNullable[Date] ~
    (__ \ "isPackage").writeNullable[Boolean] ~
    (__ \ "isOem").writeNullable[Boolean]
  ) (unlift(Product.unapply))


  implicit object ProductWriter extends BSONDocumentWriter[Product] {
    import reactivemongo.bson.DefaultBSONHandlers._
    import org.opencommercesearch.bson.BSONFormats._
    import reactivemongo.bson._

    def write(product: Product): BSONDocument = BSONDocument(
      "_id" -> product.id,
      "title" -> product.title,
      "description" -> product.description,
      "shortDescription" -> product.shortDescription,
      "brand" -> product.brand,
      "gender" -> product.gender,
      "sizingChart" -> product.sizingChart,
      "detailImages" -> product.detailImages,
      "bulletPoints" -> product.bulletPoints,
      "attributes" -> product.attributes,
      "features" -> product.features,
      "listRank" -> product.listRank,
      "customerReviews" -> product.customerReviews,
      "freeGifts" -> product.freeGifts,
      "categories" -> product.categories,
      "skus" -> product.skus,
      "generation" -> product.generation,
      "activationDate" -> product.activationDate,
      "isPackage" -> product.isPackage,
      "isOem" -> product.isOem
    )
  }

  implicit object ProductReader extends BSONDocumentReader[Product] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): Product = Product(
      doc.getAs[String]("_id"),
      doc.getAs[String]("title"),
      doc.getAs[String]("description"),
      doc.getAs[String]("shortDescription"),
      doc.getAs[Brand]("brand"),
      doc.getAs[String]("gender"),
      doc.getAs[String]("sizingChart"),
      doc.getAs[Seq[Image]]("detailImages"),
      doc.getAs[Seq[String]]("bulletPoints"),
      doc.getAs[Seq[Attribute]]("attributes"),
      doc.getAs[Seq[Attribute]]("features"),
      doc.getAs[Int]("listRank"),
      doc.getAs[CustomerReview]("customerReviews"),
      doc.getAs[Map[String, Seq[Product]]]("freeGifts"),
      None, // avaialabilityStatus is a calculated field
      doc.getAs[Seq[Category]]("categories"),
      doc.getAs[Seq[Sku]]("skus"),
      doc.getAs[Generation]("generation"),
      doc.getAs[Date]("activationDate"),
      doc.getAs[Boolean]("isPackage"),
      doc.getAs[Boolean]("isOem")
    )
  }
}

case class Product (
  var id: Option[String] = None,
  var title: Option[String] = None,
  var description: Option[String] = None,
  var shortDescription: Option[String] = None,
  var brand: Option[Brand] = None,
  var gender: Option[String] = None,
  var sizingChart: Option[String] = None,
  var detailImages: Option[Seq[Image]] = None,
  var bulletPoints: Option[Seq[String]] = None,
  var attributes: Option[Seq[Attribute]] = None,
  var features: Option[Seq[Attribute]] = None,
  var listRank: Option[Int] = None,
  var customerReviews: Option[CustomerReview] = None,
  var freeGifts: Option[Map[String, Seq[Product]]] = None,
  var availabilityStatus: Option[String] = None,
  var categories: Option[Seq[Category]] = None,
  var skus: Option[Seq[Sku]] = None,
  var generation : Option[Generation] = None,
  var activationDate: Option[Date] = None,
  var isPackage: Option[Boolean] = None,
  var isOem: Option[Boolean] = None) extends IndexableElement
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

  def populateAvailabilityStatus() = {
    val order = ProductAvailabilityStatusSummary

    skus match {
      case Some(s) =>
        val sortedSkus = s.sortWith((a,b) => order(a.availabilityStatus.orNull) < order(b.availabilityStatus.orNull) )
        if (!sortedSkus.isEmpty) {
          val sku = sortedSkus.head
          availabilityStatus = sku.availabilityStatus
        } else {
          availabilityStatus = None
        }

      case None => availabilityStatus = None
    }
  }

  populateAvailabilityStatus()
}

case class ProductList(products: Seq[Product], feedTimestamp: Long) {

  private def isPoos(country: Country): Boolean = country.availability match {
    case Some(availability) => availability.status.orNull == Availability.PermanentlyOutOfStock
    case None => false
  }

  private def hasNonPoos(sku: Sku): Boolean = sku.countries match {
    case Some(countries) => countries.collectFirst {
      case country if !isPoos(country) => true
    }.getOrElse(false)
    case None => false
  }

  def toDocuments(service: CategoryService)(implicit context: Context) : util.List[SolrInputDocument] = {
    def setSize(doc: SolrInputDocument, size: Size) = {
      for (sizeName <- size.name) {
        doc.setField("size", sizeName)
      }
      for (scale <- size.scale) {
        doc.setField("scale", scale)
      }
    }

    val skuDocuments = new util.ArrayList[SolrInputDocument](products.size * 3)

    var expectedDocCount = 0
    var currentDocCount = 0
    var currentNonIndexableProductCount = 0

    for (product: Product <- products) {
      for (productId <- product.id; title <- product.title; brand <- product.brand;
           skus <- product.skus; listRank <- product.listRank) {
        val isOem = product.isOem.getOrElse(false)
        val nonPoosSku = skus.collectFirst {
          case sku: Sku if hasNonPoos(sku) => sku
        }

        if (nonPoosSku.isDefined && (IndexOemProductsEnabled || !isOem)) {
          var gender: String = null
          var activationDate: Date = null

          for (g <- product.gender) { gender = g }
          for (activeDate <- product.activationDate) { activationDate = activeDate }

          val skuCount = skus.size
          var skuSort = 0

          for (sku: Sku <- skus) {
            if (hasNonPoos(sku)) {
              expectedDocCount += 1

              for (id <- sku.id; image <- sku.image; isRetail <- sku.isRetail;
                   isCloseout <- sku.isCloseout; countries <- sku.countries) {
                currentDocCount += 1
                val doc = new SolrInputDocument()

                doc.setField("id", id)
                doc.setField("productId", productId)
                doc.setField("title", title)
                for (brandId <- brand.id) {
                  doc.setField("brandId", brandId)
                }
                for (brandName <- brand.name) {
                  doc.setField("brand", brandName)
                }
                for (imageUrl <- image.url) {
                  doc.setField("image", imageUrl)
                }
                doc.setField("listRank", listRank)
                doc.setField("isRetail", isRetail)
                doc.setField("skuCount", skuCount)
                doc.setField("isCloseout", isCloseout)
                for (isOutlet <- sku.isOutlet) {
                  doc.setField("isOutlet", isOutlet)
                }
                for (isPastSeason <- sku.isPastSeason) {
                  doc.setField("isPastSeason", isPastSeason)
                }
                if (gender != null) {
                  doc.setField("gender", gender)
                }

                for(generation <- product.generation) {
                  for(number <- generation.number) {
                    doc.setField("generation_number", number)
                  }
                  for(master <- generation.master) {
                    doc.setField("generation_master",master.getId)
                  }
                }

                if (activationDate != null) {
                  doc.setField("activationDate", activationDate)
                }

                for (year <- sku.year) {
                  doc.setField("year", year)
                }
                for (season <- sku.season) {
                  doc.setField("season", season)
                }

                for (color <- sku.color) {
                  for (name <- color.name; family <- color.family) {
                    doc.setField("colorFamily", family)
                    doc.setField("color", name)
                  }
                }

                for (catalogs <- sku.catalogs) {
                  service.loadCategoryPaths(doc, product, catalogs)
                }

                for (size <- sku.size) {
                  setSize(doc, size.preferred.getOrElse(size))
                }

                for (reviews <- product.customerReviews) {
                  doc.setField("reviews", reviews.count.getOrElse(0))
                  doc.setField("reviewAverage", reviews.average.getOrElse(0.0))
                  doc.setField("bayesianReviewAverage", reviews.bayesianAverage.getOrElse(0.0))
                }

                for (country: Country <- countries) {
                  for (code <- country.code) {
                    doc.addField("country", code)

                    for (listPrice <- country.listPrice) {
                      doc.setField("listPrice" + code, listPrice)
                    }

                    for (salePrice <- country.salePrice) {
                      doc.setField("salePrice" + code, salePrice)
                    }
                    for (discountPercent <- country.discountPercent) {
                      doc.setField("discountPercent" + code, discountPercent)
                    }
                    for (onSale <- country.onSale) {
                      doc.setField("onsale" + code, onSale)
                    }

                    for (availability <- country.availability) {
                      for (status <- availability.status; stockLevel <- availability.stockLevel) {
                        doc.setField("stockLevel" + code, stockLevel)
                        doc.setField("isToos", status == Availability.OutOfStock)
                      }
                      doc.setField("allowBackorder" + code, country.allowBackorder.getOrElse(false))
                    }
                    for (url <- country.url) {
                      doc.setField("url" + code, url)
                    }
                  }
                }

                for (freeGifts <- product.freeGifts) {
                  freeGifts.foreach { case (catalog, gifts) =>
                      doc.setField("freeGift" + catalog, true)
                  }
                }

                for (features <- product.features) {
                  setAttributes(doc, features, FeatureFieldNamePrefix)
                }
                for (attributes <- product.attributes) {
                  setAttributes(doc, attributes, AttrFieldNamePrefix)
                }
                doc.setField("sort", skuSort)
                skuSort += 1
                doc.setField("indexStamp", feedTimestamp)
                skuDocuments.add(doc)
              }
            }
          }
        } else {
          currentNonIndexableProductCount += 1

          if (nonPoosSku.isEmpty) {
            Logger.info(s"Product $productId is permanently out of stock and won't be index in search")
          } else {
            Logger.info(s"Product $productId is original equipment manufacturer and won't be index in search")
          }
        }
      }

      if ((expectedDocCount != currentDocCount) || (expectedDocCount == 0 && currentNonIndexableProductCount == 0)) {
        throw new IllegalArgumentException("Missing required fields for product " + product.id.get)
      }

    }
    skuDocuments
  }

  def setAttributes(doc: SolrInputDocument, attributes: Seq[Attribute], prefix: String) : Unit = {
    for (attribute <- attributes) {
      val searchable = attribute.searchable.getOrElse(true)

      for (name <- attribute.name; value <- attribute.value) {
        if (searchable) {
          doc.addField(prefix + name.toLowerCase.replaceAll(AttrNameCleanupPattern, ""), value)
        }
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
