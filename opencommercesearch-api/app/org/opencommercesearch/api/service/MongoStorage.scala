package org.opencommercesearch.api.service

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

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.collection.JavaConversions._
import com.mongodb.{MongoClient, WriteResult}
import com.mongodb.gridfs.GridFS
import org.opencommercesearch.api.models._
import org.jongo.Jongo
import play.api.Logger
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.lang.StringUtils

/**
 * A storage implementation using MongoDB
 *
 * @author rmerizalde
 */
class MongoStorage(mongo: MongoClient) extends Storage[WriteResult] {
  val NoSite = null
  var jongo: Jongo = null
  var gridfs: GridFS = null

  lazy val ProjectionMappings = Map(
    "skus.allowBackorder" -> "skus.countries.allowBackorder",
    "skus.listPrice" -> "skus.countries.listPrice",
    "skus.salePrice" -> "skus.countries.salePrice",
    "skus.discountPercent" -> "skus.countries.discountPercent",
    "skus.discountPercent" -> "skus.countries.discountPercent",
    "skus.url" -> "skus.countries.url",
    "skus.availability" -> "skus.countries.availability",
    "skus.availability.status" -> "skus.countries.availability.status",
    "skus.availability.stockLevel" -> "skus.countries.availability.stockLevel",
    "skus.stockLevel" -> "skus.countries.stockLevel", // @todo deprecated this
    "skus.availability.backorderLevel" -> "skus.countries.availability.backorderLevel"
  )

  val DefaultSearchProjection =
    """
      | title:1, brand.name:1, customerReviews.count:1, customerReviews.average:1, skus.countries.stockLevel:1, hasFreeGift:1,
      | skus.countries.availability.stockLevel:1, skus.countries.availability.status:1, skus.isPastSeason:1, skus.countries.code:1,
      | skus.countries.listPrice:1, skus.countries.salePrice:1, skus.countries.discountPercent:1, skus.countries.onSale:1,
      | skus.countries.url:1, skus.catalogs:1, skus.image:1, skus.title:1, skus.isRetail:1, skus.isCloseout:1, skus.isOutlet:1, skus.id:1
    """.stripMargin

  val DefaultProductProject =
    """
      |listRank:0, attributes.searchable:0, features.searchable:0, skus.season:0, skus.year:0, skus.isRetail:0, skus.isCloseout:0
    """.stripMargin

  val DefaultCategoryProject =
    """
      |childCategories:0, parentCategories:0, isRuleBased:0, catalogs:0
    """.stripMargin
  
  val DefaultBrandProject =
    """
      |logo:0, url:0, sites:0
    """.stripMargin

  val DefaultFacetProject = StringUtils.EMPTY

  def setJongo(jongo: Jongo) : Unit = {
    this.jongo = jongo
  }

  def setGridFs(gridfs: GridFS) : Unit = {
    this.gridfs = gridfs
  }

  /**
   * Keep Mongo indexes to the minimum, specially if it saves a roundtrip to Solr for simple things
   */
  def ensureIndexes() : Unit = {
    jongo.getCollection("products").ensureIndex("{skus.catalogs: 1}", "{sparse: true, name: 'sku_catalog_idx'}")
    jongo.getCollection("products").ensureIndex("{skus.countries.code: 1}", "{sparse: true, name: 'sku_country_idx'}")
  }

  def close() : Unit = {
    mongo.close()
  }

  def countProducts() : Future[Long] = {
    Future {
      val productCollection = jongo.getCollection("products")
      productCollection.count()
    }
  }

  def findProducts(ids: Seq[(String, String)], country: String, fields: Seq[String], isSearch:Boolean) : Future[Iterable[Product]] = {
    findProducts(ids, NoSite, country, fields, isSearch)
  }

  def findProducts(ids: Seq[(String, String)], site:String, country: String, fields: Seq[String], minimumFields:Boolean) : Future[Iterable[Product]] = {
    Future {
      if (ids.size > 0) {

        // the product query is like {$and: [{_id: {$in: [#,..,#]}}, {skus: {$elemMatch: {countries.code:#, catalogs:#}}}]}

        val productCollection = jongo.getCollection("products")
        val query = new StringBuilder(128)
          .append("{ $and: [{_id: {$in: [")

        //for ids
        ids.foreach(t => query.append("#,"))

        query.setLength(query.length - 1)
        query.append("]}}, { skus: { $elemMatch: { countries.code:#")

        val skuCount = if (minimumFields) ids.size else 0
        val parameters = new ArrayBuffer[Object](ids.size + 1)

        parameters.appendAll(ids.map(t => t._1))
        parameters.append(country)

        if (site != null) {
          query.append(", catalogs:#")
          parameters.append(site)
        }
        query.append(" }}}]}")

        val products = productCollection.find(query.toString(), parameters: _*)
          .projection(projectProduct(site, fields, skuCount), ids.map(t => t._2).filter(_ != null): _*).as(classOf[Product]).toSeq
        val productSort = Map(ids.map { case (productId, _) => productId } zip (1 to ids.size): _*)
        products.map { p => filterSearchProduct(site, country, p, minimumFields, fields) } sortWith { case (p1, p2) =>
          (p1.id, p2.id) match {
            case (Some(id1), Some(id2)) => productSort(id1) <= productSort(id2)
            case _ => true
          }
        }
      } else {
        Seq.empty[Product]
      }
    }
  }

  def findProduct(id: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(NoSite, country,
        productCollection.findOne("{$and: [{_id:#}, {skus.countries.code:#}]}", id, country).projection(projectProduct(NoSite, fields)).as(classOf[Product]),
        fields)
    }
  }

  def findProduct(id: String, site: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(site, country,
        productCollection.findOne("{$and: [{_id:#}, {skus: {$elemMatch: {countries.code:#, catalogs:#}}}]}", id, country, site)
          .projection(projectProduct(site, fields)).as(classOf[Product]), fields)
    }
  }

  /**
   * Filters the skus by the given country
   * @param country the country to filter by
   * @param product the product which skus will be filtered
   * @return the product
   */
  private def filterSkus(site: String, country: String, product: Product, fields: Seq[String]) : Product = {
    if (product != null) {
      for (skus <- product.skus) {
        product.skus = Option(skus.filter { s =>
          var includeSku = true

          if (site != NoSite) {
            includeSku = s.catalogs match {
              case Some (catalogs) =>
                catalogs.contains(site)
              case None =>
                Logger.warn(s"Cannot filter by site '$site', no sites found for sku '${s.id.orNull}'")
                false
            }
          }
          if (!includesSkuField("skus.catalogs", fields, isDefault = false)) {
            s.catalogs = None
          }
          includeSku && flattenCountries(country, s)
        })
      }

      if (includesProductField("availabilityStatus", fields, isDefault = true)) {
        product.populateAvailabilityStatus()
      }
    }

    product
  }

  private def includesProductField(field: String, fields: Seq[String], isDefault: Boolean = false) = {
    includesField(field, fields, "*", isDefault)
  }

  private def includesSkuField(field: String, fields: Seq[String], isDefault: Boolean = false) = {
    includesField(field, fields, "skus", isDefault)
  }

  /**
   * Checks if the given field is included in the fields
   * @param field the field to check
   * @param fields the field list
   * @param nestedStarField the * representation for a nested field. For example, skus.* -> skus
   * @param isDefault true if the field should be include when no field list is empty. Otherwise false
   * @return true if the the field declaration includes the given field. If not, returns false
   */
  private def includesField(field: String, fields: Seq[String], nestedStarField: String, isDefault: Boolean = false) = {
    def includes(fields: Seq[String]): Boolean = fields match {
      case "*" +: xs => true
      case `nestedStarField` +: xs => true
      case x +: xs => if (x.equals(field)) true else includes(xs)
      case Seq() => false
    }
    fields match {
      case Nil => isDefault
      case _ => includes(fields)
    }
  }

  private def filterSearchProduct(site: String, country: String, product: Product, minimumFields:Boolean, fields: Seq[String]) : Product = {
    def isSkusFieldIn(field: String) = field.equals("*") || field.equals("skus") || field.startsWith("skus.")

    val filteredProduct = filterSkus(site, country, product, fields)

    if(minimumFields) {
      //Clean up product data if skus weren't requested originally
      val skuFields = fields.isEmpty || (fields collectFirst {
        case field: String if isSkusFieldIn(field) => true
      }).getOrElse(false)

      if (!skuFields) {
        product.skus = None
      }

    }
    product
  }

  /**
   * Every request has a single locale. That means he have a single country per sku. This method filter the country list
   * in the skus and flattens the country properties to the sku properties
   *
   * @param country the locale country
   * @param sku the sku to filter & flatten
   * @return true if the skus had the given country, otherwise returns false
   */
  private def flattenCountries(country: String, sku: Sku) : Boolean = {
    var filteredCountries: Seq[Country] = null
    for (countries <- sku.countries) {
      filteredCountries = countries.filter((c: Country) => country.equals(c.code.get))
    }

    if (filteredCountries != null && filteredCountries.size > 0) {
      val country = filteredCountries(0)
      sku.allowBackorder = country.allowBackorder

      sku.listPrice = country.listPrice
      sku.salePrice = country.salePrice
      sku.discountPercent = country.discountPercent
      sku.url = country.url
      sku.availability = country.availability

      if (sku.availability.isEmpty) {
        // support backward compatibility
        if (country.stockLevel.isDefined) {
          val skuStatus = country.stockLevel match {
            case Some(0) | Some(Availability.InfiniteStock) => country.allowBackorder match {
              case Some(allowed) => Some(if (allowed) Availability.Backorderable else Availability.OutOfStock)
              case None => None
            }
            case Some(_) => Some(Availability.InStock)
            case None => None
          }
          sku.availability = Some(new Availability(
            status = skuStatus,
            stockLevel = country.stockLevel
          ))
        }
      }
    }
    sku.countries = None
    filteredCountries != null && filteredCountries.size > 0
  }

  /**
   * Projects the data that will be return in the response
   * @param fields is the list of fields to return. Fields for nested documents are fully qualified (e.g. skus.color)
   * @return the projection
   */
  private def projectProduct(site: String, fields: Seq[String]) : String = {
    projectProduct(site, fields, 0)
  }

  /**
   * Projects the data that will be return in the response. If sku count is greater than zero, the projection will include
   * the elemMatch to target a single sku for each product. Additionally, the default included fields in the projection
   * vary depending on sku count. Usually, sku count greater than zero is used for searches.
   *
   * @param site is the site to filter by
   * @param fields is the list of fields to return. Fields for nested documents are fully qualified (e.g. skus.color)
   * @param skuCount indicates weather or not the project will target a single sku per project
   * @return the projection
   */
  private def projectProduct(site: String, fields: Seq[String], skuCount: Int) : String = {
    val projection = new StringBuilder(128)
    var projectionFields = new StringBuilder(128)

    if(fields.contains("*")) {
      projection.append("{}")
    }
    else {
        projection.append("{")
        if (fields.size > 0) {
          var includeSkus = false

          fields.foreach(field => {
            val f = ProjectionMappings.getOrElse(field, field)
            projectionFields.append(f).append(":1,")
            if (f.startsWith("skus.")) {
              includeSkus = true
            }
          })
          if (includeSkus) {
            // we can't use elemMatch on nested fields. The country list is small so all countries are loaded and filtered
            // in-memory. If skus are been return as part of the response we need to force the country code to do the filtering.
            // Country properties are flatten into the sku level so the code will never be part of the response
            projectionFields.append("skus.countries.code:1,")
            if (site != NoSite) {
              projectionFields.append("skus.catalogs:1,")
            }
          }

        } else {
          if (skuCount > 0) {
            projectionFields.append(DefaultSearchProjection)
          } else {
            projectionFields.append(DefaultProductProject)
          }
        }
    }
    
    if (skuCount > 0) {
      projection.append(" skus: { $elemMatch: {$or: [")
      for (x <- 1 to skuCount) {
        projection.append("{id:#},")
      }
      projection.setLength(projection.length - 1)
      projection.append("]}},")
    }

    projection.append(projectionFields.toString())
    projection.append("}")
    projection.toString()
  }

  /**
   * Creates a projection for the given list of fields (adds includes/excludes). If fields is only *, then no projection is created.
   * @param fields Fields used to created the projection for (include fields)
   * @param defaultFieldsToHide Fields that will be ignored from the projection (exclude fields)
   * @return A projection to be used while querying Mongo storage.
   */
  private def projectionAux(fields: Seq[String], defaultFieldsToHide: String) : String = {
    //If star is provided, then return everything except for hierarchyTokens
    if(fields.contains("*")) {
      return "{hierarchyTokens:0}"
    }

    val projection = new StringBuilder(128)
    projection.append("{")
    if (fields.size > 0) {
      var includeSkus = false
      fields.foreach(field => {
        val f = ProjectionMappings.getOrElse(field, field)
        projection.append(f).append(":1,")
        if (f.startsWith("skus.")) {
          includeSkus = true
        }
      })
    } else {
      projection.append(defaultFieldsToHide)
    }
    projection.append("}")
    projection.toString()
  }
  
  private def projectionCategory(fields: Seq[String]) : String = {
    projectionAux(fields, DefaultCategoryProject)
  }
    
  private def projectionBrand(fields: Seq[String]) : String = {
    projectionAux(fields, DefaultBrandProject)
  }

  private def projectionFacet(fields: Seq[String]) : String = {
    projectionAux(fields, DefaultFacetProject)
  }

  def saveProduct(product: Product*) : Future[WriteResult] = {
    Future {
      val productCollection = jongo.getCollection("products")
      var result: WriteResult = null
      product.map( p => result = productCollection.save(p) )
      result
    }
  }
  
  def saveCategory(category: Category*) : Future[WriteResult] = {
    Future {
      val categoryCollection = jongo.getCollection("categories")
      var result: WriteResult = null
      category.map( c => result = categoryCollection.save(c) )
      result
    }
  }

  def findCategory(id: String, fields: Seq[String]) : Future[Category] = {
    Future {
      var hasChildCategories, hasParentCategories : Boolean = false
      for (f <- fields) {
        hasChildCategories = fields.contains("childCategories")
        hasParentCategories = fields.contains("parentCategories")
      }

      val categoryCollection = jongo.getCollection("categories")
      if(hasChildCategories && hasParentCategories) {
        mergeNestedCategories(id, categoryCollection.find("{ $or : [{_id:#}, { childCategories._id:#}, { parentCategories._id:#}] }", id, id, id).projection(projectionCategory(fields)).as(classOf[Category]))
      } else if(hasChildCategories) {
        mergeNestedCategories(id, categoryCollection.find("{ $or : [{_id:#}, { parentCategories._id:#}] }", id, id).projection(projectionCategory(fields)).as(classOf[Category]))
      } else if(hasParentCategories) {
        mergeNestedCategories(id, categoryCollection.find("{ $or : [{_id:#}, { childCategories._id:#}] }", id, id).projection(projectionCategory(fields)).as(classOf[Category]))
      } else {
        categoryCollection.findOne("{_id:#}", id).projection(projectionCategory(fields)).as(classOf[Category])
      }
    }
  }

  def findAllCategories(fields: Seq[String]) : Future[Iterable[Category]] = {
    Future {
      val categoryCollection = jongo.getCollection("categories")
      categoryCollection.find().projection(projectionCategory(fields)).as(classOf[Category])
    }
  }

  def findCategories(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Category]] = {
    Future {
      val categoryCollection = jongo.getCollection("categories")
      categoryCollection.find("{_id:{$in:#}}", ids).projection(projectionCategory(fields)).as(classOf[Category])
    }
  }
  
  def saveBrand(brand: Brand*) : Future[WriteResult] = {
    Future {
      val brandCollection = jongo.getCollection("brands")
      var result: WriteResult = null
      brand.map( b => result = brandCollection.save(b) )
      result
    }
  }
  
  def findBrand(id: String, fields: Seq[String]) : Future[Brand] = {
    Future {
      val brandCollection = jongo.getCollection("brands")
      brandCollection.findOne("{_id:#}", id).projection(projectionBrand(fields)).as(classOf[Brand])
    }
  }
  
  def findBrands(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Brand]] = {
    Future {
      val brandCollection = jongo.getCollection("brands")
      brandCollection.find("{_id:{$in:#}}", ids).projection(projectionBrand(fields)).as(classOf[Brand])
    }
  }

  def saveFacet(facet: Facet*) : Future[WriteResult] = {
    Future {
      val facetCollection = jongo.getCollection("facets")
      var result: WriteResult = null
      facet.map( c => result = facetCollection.save(c) )
      result
    }
  }

  def findFacet(id: String, fields: Seq[String]) : Future[Facet] = {
    Future {
      val facetCollection = jongo.getCollection("facets")
      facetCollection.findOne("{_id:#}", id).projection(projectionFacet(fields)).as(classOf[Facet])
    }
  }

  def findFacets(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Facet]] = {
    Future {
      val facetCollection = jongo.getCollection("facets")
      facetCollection.find("{_id:{$in:#}}", ids).projection(projectionFacet(fields)).as(classOf[Facet])
    }
  }

  private def mergeNestedCategories(id: String, categories : java.lang.Iterable[Category]) : Category = {
    val lookupMap = mutable.HashMap.empty[String, Category]
        var mainDoc: Category = null
        if (categories != null) {
            categories.foreach(
                doc => {
                  val currentId = doc.getId
                  lookupMap += (currentId -> doc)
                  if( id.equals(doc.getId) ) {
                    mainDoc = doc
                  }
                  Logger.debug("Found category " + id)
                }
            )
            if(mainDoc != null) {
                addNestedCategoryNames(mainDoc.childCategories, lookupMap)
                addNestedCategoryNames(mainDoc.parentCategories, lookupMap)
            } 
        }
        mainDoc
  }
  
  private def addNestedCategoryNames(categories: Option[Seq[Category]], lookupMap: mutable.HashMap[String, Category] ) = {
    for( cats <- categories) {
      cats.foreach(
        category => {
          for (id <- category.id) {
            if(lookupMap.contains(id)) {
              val newDoc : Category =  lookupMap(id)
              category.name = newDoc.name
              category.seoUrlToken = newDoc.seoUrlToken
              category.sites= newDoc.sites
            } else {
              Logger.error(s"Missing nested category id reference [$id]")
            }
          }
        }
      )
    }
  }
}
