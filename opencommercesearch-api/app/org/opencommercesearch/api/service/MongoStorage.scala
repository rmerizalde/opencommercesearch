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

import java.sql.Timestamp
import java.util.Calendar

import org.opencommercesearch.api.models._
import org.opencommercesearch.api.service.MongoStorage._
import org.opencommercesearch.api.Global.{FilterLiveProductsEnabled}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import reactivemongo.api.DefaultDB
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONArray, BSONInteger}
import reactivemongo.core.commands.{Count, LastError}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.Future

/**
 * A storage implementation using MongoDB
 *
 * @author rmerizalde
 */
class MongoStorage(database: DefaultDB) extends Storage[LastError] {
  val productCollection = database[BSONCollection]("products")
  val brandCollection = database[BSONCollection]("brands")
  val categoryCollection = database[BSONCollection]("categories")
  val facetCollection = database[BSONCollection]("facets")
  val ruleCollection = database[BSONCollection]("rules")
  val contentCollection = database[BSONCollection]("content")

  /**
   * Keep Mongo indexes to the minimum, specially if it saves a roundtrip to Solr for simple things
   */
  def ensureIndexes() : Unit = {
    val productIndexesManger = productCollection.indexesManager
    val skuCatalogIndex = new Index(key = Seq(("skus.catalogs", IndexType.Ascending)), name = Some("sku_catalog_idx"), sparse = true)
    val skuCountryIndex = new Index(key = Seq(("skus.catalogs", IndexType.Ascending)), name = Some("sku_country_idx"), sparse = true)
    val skuLaunchDate = new Index(key = Seq(("skus.countries.launchDate", IndexType.Ascending)), name = Some("sku_launchdate_idx"), sparse = true)

    productIndexesManger.ensure(skuCatalogIndex)
    productIndexesManger.ensure(skuCountryIndex)
    productIndexesManger.ensure(skuLaunchDate)

    val brandNameIndex = new Index(key = Seq(("name", IndexType.Ascending)), name = Some("brand_name_idx"), sparse = true)

    brandCollection.indexesManager.ensure(brandNameIndex)

    val contentIdIndex = new Index(key = Seq(("productId", IndexType.Ascending)), name = Some("content_id_idx"), sparse = true)
    val contentSiteIndex = new Index(key = Seq(("site", IndexType.Ascending)), name = Some("content_site_idx"), sparse = true)

    contentCollection.indexesManager.ensure(contentIdIndex)
    contentCollection.indexesManager.ensure(contentSiteIndex)
  }

  // @todo: should we modify the interface to return Int?
  def countProducts() : Future[Long] = {
    database.command(new Count(productCollection.name)) map { count =>
      count
    }
  }

  def findProducts(ids: Seq[(String, String)], country: String, fields: Seq[String], isSearch:Boolean, preview:Boolean) : Future[Iterable[Product]] = {
    findProducts(ids, NoSite, country, fields, isSearch, preview)
  }

  def findProducts(ids: Seq[(String, String)], site:String, country: String, fields: Seq[String], minimumFields:Boolean, preview:Boolean) : Future[Iterable[Product]] = {
    if (ids.size > 0) {
      // the product query is like {$and: [{_id: {$in: [#,..,#]}}, {skus: {$elemMatch: {countries.code:#, catalogs:#}}}]}

      // or like {$and: [{_id: {$in: [#,..,#]}}, {$or: [{skus.countries.launchDate: {$exists: false}}, {skus.countries.launchDate: {$lte: now()}}]},
      //                 {skus: {$elemMatch: {countries.code:#, catalogs:#}}}]}
      var andArray = BSONArray(
        BSONDocument(
          "_id" -> BSONDocument(
            "$in" -> (ids map {t => t._1})
          ),
          "skus" -> BSONDocument(
            "$elemMatch" -> BSONDocument(
              if (site != null) {
                "countries.code" -> country
                "catalogs" -> site
              } else {
                "countries.code" -> country
              }
            )
          )
        )
      )
      if (FilterLiveProductsEnabled && !preview) {
        val currentTimeMillis = Calendar.getInstance().getTimeInMillis
        andArray = andArray ++ BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("skus.countries.launchDate" -> BSONDocument("$exists" -> false)),
            BSONDocument("skus.countries.launchDate" -> BSONDocument("$lte" -> currentTimeMillis))
          )
        )
      }
      val query = BSONDocument(
        "$and" -> andArray
      )

      val singleSku = minimumFields
      val projection = projectProduct(site, fields, minimumFields, ids.map { case (_, skuId) => skuId })
      // @todo .filter(_ != null): _*) && stream results instead of collecting
      val productFuture = productCollection
        .find(query, projection)
        .cursor[Product]
        .collect[Seq]()

      val contentFuture: Future[Iterable[ProductContent]] = if (site != NoSite) {
        findContent(ids, site)
      } else {
        Future.successful(Seq.empty[ProductContent])
      }

      productFuture flatMap { products =>
        val productSort = Map(ids.map { case (productId, _) => productId } zip (1 to ids.size): _*)
        contentFuture map { contentList => {
            //TODO transform contentList to hash to avoid traverse it n times (1 for each product)
            val result = products.map { p =>
              updateProductContent(contentList, filterSearchProduct(site, country, p, minimumFields, fields))
            } sortWith {
              case (p1, p2) => (p1.id, p2.id) match {
                case (Some(id1), Some(id2)) => productSort(id1) <= productSort(id2)
                case _ => true
              }
            }
            result
        }}
      }

    } else {
      Future.successful(Seq.empty[Product])
    }
  }

  def findProduct(id: String, country: String, fields: Seq[String]) : Future[Product] = {
    findProduct(id, NoSite, country, fields)
  }

  def findProduct(id: String, site: String, country: String, fields: Seq[String]) : Future[Product] = {
    findProducts(Seq((id, null)), NoSite, country, fields, minimumFields = false, preview = false).map(products => products.headOption.orNull)
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
        product.skus = Option(skus filter { sku =>
          var includeSku = true

          if (site != NoSite) {
            includeSku = sku.catalogs match {
              case Some (catalogs) =>
                catalogs.contains(site)
              case None =>
                Logger.warn(s"Cannot filter by site '$site', no sites found for sku '${sku.id.orNull}'")
                false
            }
          }
          if (!includesSkuField("skus.catalogs", fields, isDefault = false)) {
            sku.catalogs = None
          }

          includeSku && flattenCountries(site, country, sku)
        })
      }

      if (includesProductField("availabilityStatus", fields, isDefault = true)) {
        product.populateAvailabilityStatus()
      }
    }

    clearSkuAvailability(product, fields)
  }

  /**
   * Cleans availability data not requested. The product's availabilityStatus is a calculated field based on sku data.
   * However, user can specify what fields to request. Hence, the storage will fetch the necessary data to calculate
   * the product's availability status (if requested). If the sku's availability was not requested then it gets removed
   * from the response
   *
   * @todo revisit to see if there's a better approach (e.g. would a MongoDB aggregator work for such scenarios)
   * @param product the product to clean up
   * @param fields the requested fields
   * @return the given product
   */
  protected[service] def clearSkuAvailability(product: Product, fields: Seq[String]) = {
    def wereSkusRequested = fields.isEmpty || fields.exists(field => field == "*" || field == "skus" || field.startsWith("skus."))

    for (skus <- product.skus) {
      product.skus = Option(skus map { sku =>
        for (availability <- sku.availability) {
          for (availabilityStatus <- availability.status) {
            if (!includesField("skus.availability.status", fields, SkuAvailabilityStarFields, isDefault = true)) {
              availability.status = None
              if (!fields.exists(field => field.startsWith("skus.availability"))) {
                sku.availability = None
              }
            }
          }
        }
        sku
      })
      if (!wereSkusRequested) {
        product.skus = None
      }
    }

    product
  }

  private def includesProductField(field: String, fields: Seq[String], isDefault: Boolean = false) = {
    includesField(field, fields, ProductStarFields, isDefault)
  }

  private def includesSkuField(field: String, fields: Seq[String], isDefault: Boolean = false) = {
    includesField(field, fields, SkuStarFields, isDefault)
  }

  /**
   * Checks if the given field is included in the fields
   * @param field the field to check
   * @param fields the field list
   * @param nestedStarFields the * representations for a nested field. For example, skus.* -> skus, skus.availability.* -> skus.availability
   * @param isDefault true if the field should be include when no field list is empty. Otherwise false
   * @return true if the the field declaration includes the given field. If not, returns false
   */
  private def includesField(field: String, fields: Seq[String], nestedStarFields: Seq[String], isDefault: Boolean = false) = {
    def includes(fields: Seq[String]): Boolean = fields match {
      case "*" +: xs => true
      case x +: xs if nestedStarFields.contains(x) => true
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

    filterSkus(site, country, product, fields)

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
  private def flattenCountries(site: String, country: String, sku: Sku) : Boolean = {
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
      sku.onSale = country.onSale

      for (launchDate <- country.launchDate) {
        //only add, to the attributes, the launchDate property if the
        //filter live products feature is enabled and this current product launch date is in the future
        //this is to allow identifying not live products when the preview=false query param is available
        if (FilterLiveProductsEnabled && launchDate.after(Calendar.getInstance().getTime)) {
          val attribute = new Attribute(Some("launchDate"), Some(launchDate.toString))
          val attributeList = sku.attributes.getOrElse(Seq.empty[Attribute])
          sku.attributes = Some(attributeList ++ Some(attribute))
        }
      }

      for (defaultPrice <- country.defaultPrice) {
        sku.listPrice = defaultPrice.listPrice
        sku.salePrice = defaultPrice.salePrice
        sku.discountPercent = defaultPrice.discountPercent
      }

      for (catalogPrices <- country.catalogPrices;
        catalogPrice <- catalogPrices.get(site)) {
        for (listPrice <- sku.listPrice; price <- catalogPrice.listPrice) { sku.listPrice = catalogPrice.listPrice }
        for (salePrice <- sku.salePrice; price <- catalogPrice.salePrice) sku.salePrice = catalogPrice.salePrice
        for (discountPercent <- sku.discountPercent; discount <- catalogPrice.discountPercent) sku.discountPercent = catalogPrice.discountPercent
        for (onSale <- sku.onSale; sale <- catalogPrice.onSale) sku.onSale = catalogPrice.onSale
      }

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
  private def projectProduct(site: String, fields: Seq[String]) : BSONDocument = {
    projectProduct(site, fields, singleSku = false, Seq.empty[String])
  }

  /**
   * Projects the data that will be return in the response. If sku count is greater than zero, the projection will include
   * the elemMatch to target a single sku for each product. Additionally, the default included fields in the projection
   * vary depending on sku count. Usually, sku count greater than zero is used for searches.
   *
   * @param site is the site to filter by
   * @param fields is the list of fields to return. Fields for nested documents are fully qualified (e.g. skus.color)
   * @param singleSku indicates weather or not the project will target a single sku per project
   * @return the projection
   */
  protected[service] def projectProduct(site: String, fields: Seq[String], singleSku: Boolean, skuIds: Seq[String]) : BSONDocument = {
    var projection = if(fields.contains("*")) {
      DefaultEmptyProjection
    } else {
      if (fields.size > 0) {
        var projectedFields = fields.map(field => (mapField(field), Include))
        val includeSkus = fields.exists(field => field.startsWith("skus."))

        if (includeSkus) {
          // we can't use elemMatch on nested fields. The country list is small so all countries are loaded and filtered
          // in-memory. If skus are been return as part of the response we need to force the country code to do the filtering.
          // Country properties are flatten into the sku level so the code will never be part of the response
          projectedFields = projectedFields :+ ProjectedSkuCountryCode
          
          if (site != NoSite) {
            projectedFields = projectedFields :+ ProjectedSkuCatalog
          }

        }

        // see clearSkuAvailability
        if (includesProductField("availabilityStatus", fields, isDefault = true) && !includesField("skus.availability.status", fields, SkuAvailabilityStarFields, isDefault = true)) {
          if (!includeSkus) {
            projectedFields = projectedFields :+ ProjectedSkuCountryCode
            if (site != NoSite) {
              projectedFields = projectedFields :+ ProjectedSkuCatalog
            }
          }
          projectedFields = projectedFields :+ ProjectedSkuAvailabilityStatus
        }

        if (fields.contains("skus.listPrice") || fields.contains("skus.salePrice") || fields.contains("skus.discountPercent") || fields.contains("skus.onSale")) {
          projectedFields = projectedFields :+ ProjectedSkuCountryCatalogPrices
        }

        val projection = BSONDocument(projectedFields)

        projection
      } else if (singleSku) {
        DefaultSearchProjection
      } else {
        DefaultProductProjection
      }
    }

    if (singleSku) {
      val obj = BSONDocument(
        "$elemMatch" -> BSONDocument(
          "$or" -> skuIds.map(skuId => BSONDocument("id" -> skuId))
        )
      )

      projection = BSONDocument(("skus", obj) +: projection.elements)
    }

    projection
  }

  /**
   * Helper method to map fields
   * @param field the field to map
   * @return the field mapping. If no field mapping found returns the given field
   */
  private def mapField(field: String): String = ProjectionMappings.getOrElse(field, field)

  /**
   * Creates a projection for the given list of fields (adds includes/excludes). If fields is only *, then no projection is created.
   * @param fields Fields used to created the projection for (include fields)
   * @param defaultProjection is the default projection in case fields is empty
   * @return A projection to be used while querying Mongo storage.
   */
  private def projectionAux(fields: Seq[String], defaultProjection: BSONDocument) : BSONDocument = {
    //If star is provided, then return everything except for hierarchyTokens (hacky)
    if(fields.contains("*")) {
      return DefaultNoHierarchyTokensProjection
    }

    if (fields.size > 0) {
      val projection = BSONDocument(fields.map(field => (mapField(field), Include)))

      projection
    } else {
      defaultProjection
    }
  }
  
  private def projectCategory(fields: Seq[String]) : BSONDocument = {
    projectionAux(fields, DefaultCategoryProjection)
  }
    
  private def projectBrand(fields: Seq[String]) : BSONDocument = {
    projectionAux(fields, DefaultBrandProjection)
  }

  private def projectFacet(fields: Seq[String]) : BSONDocument = {
    projectionAux(fields, DefaultEmptyProjection)
  }

  private def projectRule(fields: Seq[String]) : BSONDocument = {
    projectionAux(fields, DefaultRuleProjection)
  }

  def saveProduct(product: Product*) : Future[LastError] = {
    // @todo: keeping backward compatible interface. Ideally, return a Future[Seq[LastError]]
    Future.sequence(product.map(p => productCollection.save(p))).map(responses => responses.lastOption.orNull)
  }

  def deleteProduct(id: String) : Future[LastError] = {
    productCollection.remove(BSONDocument("_id" -> id))
  }
  
  def saveCategory(category: Category*) : Future[LastError] = {
    // @todo: keeping backward compatible interface. Ideally, return a Future[Seq[LastError]]
    Future.sequence(category.map(c => categoryCollection.save(c))).map(responses => responses.lastOption.orNull)
  }

  def findCategory(id: String, fields: Seq[String]) : Future[Category] = {
    if (id == null) {
      return Future.successful(null)
    }

    var hasChildCategories, hasParentCategories : Boolean = false
    for (f <- fields) {
      hasChildCategories = fields.contains("childCategories")
      hasParentCategories = fields.contains("parentCategories")
    }

    val projection = projectCategory(fields)

    if (!hasChildCategories && !hasParentCategories) {
      val query = BSONDocument("_id" -> id)
      findCategories(query, projection).map(categories => categories.headOption.orNull)
    } else {
      val query = if(hasChildCategories && hasParentCategories) {
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("_id" -> id),
            BSONDocument("childCategories._id" -> id),
            BSONDocument("parentCategories._id" -> id)
          ))
      } else if(hasChildCategories) {
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("_id" -> id),
            BSONDocument("parentCategories._id" -> id)
          ))
      } else {
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("_id" -> id),
            BSONDocument("childCategories._id" -> id)
          ))
      }
      findCategories(query, projection).map(categories => mergeNestedCategories(id, categories))
    }
 }

  def findAllCategories(fields: Seq[String]) : Future[Iterable[Category]] = {
    val projection = projectCategory(fields)

    findCategories(AllQuery, projection)
  }

  def findCategories(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Category]] = {
    val query = queryById(ids)
    val projection = projectCategory(fields)

    findCategories(query, projection)
  }
  
  def saveBrand(brand: Brand*) : Future[LastError] = {
    // @todo: keeping backward compatible interface. Ideally, return a Future[Seq[LastError]]
    Future.sequence(brand.map(b => brandCollection.save(b))).map(responses => responses.lastOption.orNull)
  }
  
  def findBrand(id: String, fields: Seq[String]) : Future[Brand] = {
    val query = queryById(Seq(id))
    val projection = projectBrand(fields)

    findBrands(query, projection).map(brands => brands.headOption.orNull)
  }

  def findBrands(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Brand]] = {
    val query = queryById(ids)
    val projection = projectBrand(fields)

    findBrands(query, projection)
  }

  def findBrandsByName(names: Iterable[String], fields: Seq[String]) : Future[Iterable[Brand]] = {
    val query = queryByName(names)
    val projection = projectBrand(fields)

    findBrands(query, projection)
  }

  /**
   * Helper method to find brands in the database
   * @param query is the brand query
   * @param projection is the brand projection
   * @return a sequence of brands
   */
  private def findBrands(query: BSONDocument, projection: BSONDocument) = brandCollection
    .find(query, projection)
    .cursor[Brand]
    .collect[Seq]()

  /**
   * Helper method to find categories in the database
   * @param query is the brand query
   * @param projection is the brand projection
   * @return a sequence of categories
   */
  private def findCategories(query: BSONDocument, projection: BSONDocument) = categoryCollection
    .find(query, projection)
    .cursor[Category]
    .collect[Seq]()

  def saveFacet(facet: Facet*) : Future[LastError] = {
    // @todo: keeping backward compatible interface. Ideally, return a Future[Seq[LastError]]
    Future.sequence(facet.map(f => facetCollection.save(f))).map(responses => responses.lastOption.orNull)
  }

  def saveRule(rule: Rule*) : Future[LastError] = {
    // @todo: keeping backward compatible interface. Ideally, return a Future[Seq[LastError]]
    Future.sequence(rule.map(f => ruleCollection.save(f))).map(responses => responses.lastOption.orNull)
  }

  def findRule(id: String, fields: Seq[String]) : Future[Rule] = {
    findRules(Seq(id), fields).map(rules => rules.headOption.orNull)
  }

  def findRules(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Rule]] = {
    val query = queryById(ids)
    val projection = projectRule(fields)

    ruleCollection
      .find(query, projection)
      .cursor[Rule]
      .collect[Seq]()
  }

  def findFacet(id: String, fields: Seq[String]) : Future[Facet] = {
    findFacets(Seq(id), fields).map(facets => facets.headOption.orNull)
  }

  def findFacets(ids: Iterable[String], fields: Seq[String]) : Future[Iterable[Facet]] = {
    val query = queryById(ids)
    val projection = projectFacet(fields)

    facetCollection
      .find(query, projection)
      .cursor[Facet]
      .collect[Seq]()
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
  
  def findContent(ids: Seq[(String, String)]) : Future[Iterable[ProductContent]] = {
    findContent(ids, NoSite)
  }
  
  def findContent(ids: Seq[(String, String)], site:String) : Future[Iterable[ProductContent]] = {
    if (ids.size > 0) {

      val query = if (site == NoSite ) {
        BSONDocument(
          "productId" -> BSONDocument("$in" -> (ids map {t => t._1}))
        )
      } else {
        BSONDocument("$and" -> BSONArray(
          BSONDocument("productId" -> BSONDocument("$in" -> (ids map {t => t._1}))),
          BSONDocument("site" -> site)
        ))
      }

      val productContentFuture = contentCollection
        .find(query, DefaultProductContentProjection)
        .cursor[ProductContent]
        .collect[Seq]()

      productContentFuture.map(contentList => {
        contentList.map(elem => {
          elem.id = elem.productId
          elem.productId = None
          elem
        })
      })
    } else {
      Future.successful(Seq.empty[ProductContent])
    }
  }

  def saveProductContent(feedTimestamp: Long, site: String, contents: ProductContent*) : Future[LastError] = {
    contents.map(content => {
        content.site = Some(site)
        content.productId = content.id
        content.feedTimestamp = Some(feedTimestamp)
        content.id = Some(content.productId.get + "-" + site)
      })
    Future.sequence(contents.map(p => contentCollection.save(p))).map(responses => responses.lastOption.orNull)
  }

  def deleteContent(feedTimestamp: Long, site: String) : Future[LastError] = deleteContent(null, feedTimestamp, site)

  def deleteContent(id: String, feedTimestamp: Long, site: String) : Future[LastError] = {

    val query = if (id == null ) {
      BSONDocument("$and" -> BSONArray(
          BSONDocument("site" -> site),
          BSONDocument("$lt" -> BSONDocument("feedTimestamp" -> feedTimestamp)))
        )
      } else {
        BSONDocument("$and" -> BSONArray(
          BSONDocument("productId" -> id),
          BSONDocument("site" -> site),
          BSONDocument("$lt" -> BSONDocument("feedTimestamp" -> feedTimestamp)))
        )
      }

    contentCollection.remove(query)
  }

  private def updateProductContent(contentList: Iterable[ProductContent], product: Product): Product = {

    if (contentList.size > 0) {
      contentList.foreach(content => {
        if (content.id.getOrElse("").equals(product.id.getOrElse(""))) {
          product.description = content.description

          product.attributes = product.attributes.map(attributeList => {

            attributeList.map(attribute => {
              if (attribute.name.getOrElse("").equalsIgnoreCase(ProductContent.BottomLine)) {
                attribute.value = content.bottomLine
              }
              attribute
            })
          })
        }
      })
    }
    product
  }


  /**
   * Helper method to create a simple query to search by id
   */
  private def queryById(ids: Iterable[String]) = BSONDocument(
    "_id" -> BSONDocument(
      "$in" -> ids
    ))

  /**
   * Helper method to create a simple query to search by name
   */
  private def queryByName(names: Iterable[String]) = BSONDocument(
    "name" -> BSONDocument(
      "$in" -> names
    ))
}

object MongoStorage {
  val NoSite = null

  val Include = BSONInteger(1)

  val Exclude = BSONInteger(0)

  val ProjectionMappings = Map(
    "categories.id" -> "categories._id",
    "skus.allowBackorder" -> "skus.countries.allowBackorder",
    "skus.listPrice" -> "skus.countries.listPrice",
    "skus.salePrice" -> "skus.countries.salePrice",
    "skus.discountPercent" -> "skus.countries.discountPercent",
    // @todo this are the new mapping that will be used once the country fields are fully deprecated.
    //"skus.listPrice" -> "skus.countries.defaultPrice.listPrice",
    //"skus.salePrice" -> "skus.countries.defaultPrice.salePrice",
    //"skus.discountPercent" -> "skus.countries.defaultPrice.discountPercent",
    "skus.onSale" -> "skus.countries.onSale",
    "skus.url" -> "skus.countries.url",
    "skus.availability" -> "skus.countries.availability",
    "skus.availability.status" -> "skus.countries.availability.status",
    "skus.availability.stockLevel" -> "skus.countries.availability.stockLevel",
    "skus.stockLevel" -> "skus.countries.stockLevel", // @todo deprecated this
    "skus.availability.backorderLevel" -> "skus.countries.availability.backorderLevel"
  )

  val DefaultSearchProjection = BSONDocument(
    "title" -> Include,
    "brand.name" -> Include,
    "customerReviews.count" -> Include,
    "customerReviews.average" -> Include,
    "freeGifts" -> Include,
    "skus.countries.stockLevel" -> Include,
    "skus.countries.availability.stockLevel" -> Include,
    "skus.countries.availability.status" -> Include,
    "skus.countries.code" -> Include,
    "skus.countries.listPrice" -> Include,
    "skus.countries.salePrice" -> Include,
    "skus.countries.discountPercent" -> Include,
    "skus.countries.onSale" -> Include,
    "skus.countries.defaultPrice" -> Include,
    "skus.countries.catalogPrices" -> Include,
    "skus.countries.launchDate" -> Include,
    "skus.countries.url" -> Include,
    "skus.title" -> Include,
    "skus.catalogs" -> Include,
    "skus.image" -> Include,
    "skus.isRetail" -> Include,
    "skus.isCloseout" -> Include,
    "skus.isOutlet" -> Include,
    "skus.isPastSeason" -> Include,
    "skus.id" -> Include
  )

  val DefaultProductProjection = BSONDocument(
    "listRank" -> Exclude,
    "attributes.searchable" -> Exclude,
    "features.searchable" -> Exclude,
    "skus.season" -> Exclude,
    "skus.year" -> Exclude,
    "skus.isRetail" -> Exclude,
    "skus.isCloseout" -> Exclude
  )

  val DefaultCategoryProjection = BSONDocument(
    "childCategories" -> Exclude,
    "parentCategories" -> Exclude,
    "isRuleBased" -> Exclude,
    "catalogs" -> Exclude
  )

  val DefaultBrandProjection = BSONDocument(
    "logo" -> Exclude,
    "url" -> Exclude,
    "sites" -> Exclude
  )

  val DefaultRuleProjection = BSONDocument(
    "name" -> Include,
    "ruleType" -> Include
  )

  val DefaultProductContentProjection = BSONDocument(
    "productId" -> Include,
    "site" -> Include,
    "bottomLine" -> Include,
    "description" -> Include
  )

  val DefaultEmptyProjection = BSONDocument()

  val DefaultNoHierarchyTokensProjection = BSONDocument(
    "hierarchyTokens" -> Exclude
  )

  val ProjectedSkuCountryCode = ("skus.countries.code", Include)
  
  val ProjectedSkuCatalog = ("skus.catalogs", Include)

  val ProjectedSkuAvailabilityStatus = ("skus.countries.availability.status", Include)

  val ProjectedSkuCountryCatalogPrices = ("skus.countries.catalogPrices", Include)

  val AllQuery = BSONDocument()

  val ProductStarFields = Seq("*")

  val SkuStarFields = Seq("skus")

  val SkuAvailabilityStarFields = Seq("skus.availability") ++ SkuStarFields
}