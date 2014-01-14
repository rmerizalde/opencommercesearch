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
import org.opencommercesearch.api.models.{Country, Sku, Product}
import org.jongo.Jongo

/**
 * A storage implementation using MongoDB
 *
 * @author rmerizalde
 */
class MongoStorage(mongo: MongoClient) extends Storage[WriteResult] {

  var jongo: Jongo = null
  var gridfs: GridFS = null

  lazy val ProjectionMappings = Map(
    "skus.allowBackorder" -> "skus.countries.allowBackorder",
    "skus.listPrice" -> "skus.countries.listPrice",
    "skus.salePrice" -> "skus.countries.salePrice",
    "skus.discountPercent" -> "skus.countries.discountPercent",
    "skus.discountPercent" -> "skus.countries.discountPercent",
    "skus.url" -> "skus.countries.url",
    "skus.stockLevel" -> "skus.countries.stockLevel"
  )

  val DefaultSearchProjection =
    """
      |title:1, brand.name:1, isOutOfStock:1, reviewCount:1, reviewAverage:1, skus.isPastSeason:1, skus.countries.code:1,
      | skus.countries.listPrice:1, skus.countries.salePrice:1, skus.countries.discountPercent:1, skus.countries.onSale:1,
      | skus.countries.url:1,
    """.stripMargin

  val DefaultProductProject =
    """
      |listRank:0, categories:0, skus.season:0, skus.year:0, skus.countries.allowBackorder:0, skus.isRetail: 0,
      |skus.isCloseout: 0, skus.catalogs: 0, skus.customSort: 0,
    """.stripMargin

  def setJongo(jongo: Jongo) : Unit = {
    this.jongo = jongo
  }

  def setGridFs(gridfs: GridFS) : Unit = {
    this.gridfs = gridfs
  }

  /**
   * Keep Mongo indexes to the minimum, specially if it saves a roundtrip to Solr for simple things
   */
  def ensureIndexes : Unit = {
    jongo.getCollection("products").ensureIndex("{skus.catalogs: 1}", "{sparse: true, name: 'sku_catalog_idx'}")
    jongo.getCollection("products").ensureIndex("{skus.countries.code: 1}", "{sparse: true, name: 'sku_country_idx'}")
  }

  def close : Unit = {
    mongo.close()
  }

  def findProducts(ids: Seq[Tuple2[String, String]], country: String, fields: Seq[String]) : Future[Iterable[Product]] = {
    Future {
      val productCollection = jongo.getCollection("products")
      val query = new StringBuilder(128)
        .append("{ $or: [")

      ids.foreach(t => query.append("{_id:#},"))
      query.setLength(query.length - 1)
      query.append("]}")

      val products = productCollection.find(query.toString(), ids.map(t => t._1):_*)
        .projection(projection(fields, ids.size), ids.map(t => t._2):_*).as(classOf[Product])
      products.map(p => filterSearchProduct(country, p))
    }
  }


  def findProduct(id: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(country, productCollection.findOne("{_id:#, skus.countries.code:#}", id, country).projection(projection(fields)).as(classOf[Product]))
    }
  }

  def findProduct(id: String, site: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(country, productCollection.findOne("{_id:#, skus.catalogs:#, skus.countries.code:#}", id, site, country).projection(projection(fields)).as(classOf[Product]))
    }
  }

  /**
   * Filters the skus by the given country
   * @param country the country to filter by
   * @param product the product which skus will be filtered
   * @return the product
   */
  private def filterSkus(country: String, product: Product) : Product = {
    if (product != null) {
      for (skus <- product.skus) {
        product.skus = Some(skus.filter((s: Sku) => {
          flattenCountries(country, s)
        }))
      }
    }
    product
  }

  private def filterSearchProduct(country: String, product: Product) : Product = {
    for (skus <- product.skus) {
      skus.foreach(s => {
        flattenCountries(country, s)
        // @todo mixing exlclude and includes in a project is currently not supported
        // https://jira.mongodb.org/browse/SERVER-391
        // In the meanwhile, we force hiding some sku properties that are usually not needed in search
        s.size = None
        s.catalogs = None
        s.customSort = None
        s.colorFamily = None
        s.year = None
        s.season = None
      })
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

    if (filteredCountries.size > 0) {
      val country = filteredCountries(0)
      sku.allowBackorder = country.allowBackorder
      sku.listPrice = country.listPrice
      sku.salePrice = country.salePrice
      sku.discountPercent = country.discountPercent
      sku.url = country.url
      sku.stockLevel = country.stockLevel
    }
    sku.countries = None
    filteredCountries.size > 0
  }

  /**
   * Projects the data that will be return in the response
   * @param fields is the list of fields to return. Fields for nested documents are fully qualified (e.g. skus.color)
   * @return the projection
   */
  private def projection(fields: Seq[String]) : String = {
    projection(fields, 0)
  }

  /**
   * Projects the data that will be return in the response. If sku count is greater than zero, the projection will include
   * the elemMatch to target a single sku for each product. Additionally, the default included fields in the projection
   * vary depending on sku count. Usually, sku count greater than zero is used for searches.
   *
   * @param fields is the list of fields to return. Fields for nested documents are fully qualified (e.g. skus.color)
   * @param skuCount indicates weather or not the project will target a single sku per project
   * @return the projection
   */
  private def projection(fields: Seq[String], skuCount: Int) : String = {
    val projection = new StringBuilder(128)
    projection.append("{")
    if (fields.size > 0) {
      var includeSkus = false
      fields.map(f => ProjectionMappings.get(f).getOrElse(f)).foreach(f => {
        projection.append(f).append(":1,")
        if (f.startsWith("skus.")) {
          includeSkus = true
        }
      })
      if (includeSkus) {
        // we can't use elemMatch on nested fields. The country list is small so all countries are loaded and filtered
        // in-memory. If skus are been return as part of the response we need to force the country code to do the filtering.
        // Country properties are flatten into the sku level so the code will never be part of the response
        projection.append("skus.countries.code:1,")
      }
    } else {
      if (skuCount > 0) {
        projection.append(DefaultSearchProjection)

      } else {
        projection.append(DefaultProductProject)
      }
    }

    if (skuCount > 0) {
      projection.append(" skus: { $elemMatch: {$or: [")
      for (x <- 1 to skuCount) {
        projection.append("{id:#},")
      }
      projection.setLength(projection.length - 1)
      projection.append("]}}}")
    }

    projection.append("}")
    projection.toString()
  }

  def saveProduct(product: Product*) : Future[WriteResult] = {
    Future {
      val productCollection = jongo.getCollection("products")
      var result: WriteResult = null
      product.map( p => result = productCollection.update(s"{_id: '${p.getId()}'}").upsert().merge(p) )
      result
    }
  }
}
