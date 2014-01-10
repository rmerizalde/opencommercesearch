package org.opencommercesearch.api.service

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

import com.mongodb.{MongoClient, WriteResult}
import com.mongodb.gridfs.GridFS
import org.opencommercesearch.api.models.{Country, Sku, Product}
import org.jongo.Jongo
import play.api.Logger


/**
 * A storage implementation using MongoDB
 *
 * @author rmerizalde
 */
class MongoStorage(mongo: MongoClient) extends Storage[WriteResult] {

  var jongo: Jongo = null
  var gridfs: GridFS = null

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


  def findProduct(id: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(country, productCollection.findOne("{_id:#, skus.countries.code:#}", id, country).projection(projection(fields)).as(classOf[Product]))
    }
  }

  def findProduct(id: String, site: String, country: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      filterSkus(country, productCollection.findOne("{_id:#, skus.catalogs:#, skus.countries.code:#}", id, country, site).projection(projection(fields)).as(classOf[Product]))
    }
  }

  /**
   * Filters the skus by the given country
   * @param country the country to filter by
   * @param product the product which skus will be filtered
   * @return the product
   */
  private def filterSkus(country: String, product: Product) : Product = {
    for (skus <- product.skus) {
      product.skus = Some(skus.filter((s: Sku) => {
        var filteredCountries: Seq[Country] = null
        for (countries <- s.countries) {
          filteredCountries = countries.filter((c: Country) => country.equals(c.code.get))
        }
        if (filteredCountries.size > 0) {
          s.countries = Some(filteredCountries)
        }
        filteredCountries.size > 0
      }))
    }
    product
  }

  private def projection(fields: Seq[String]) : String = {
    val projection = new StringBuilder(128)
    projection.append("{")
    if (fields.size > 0) {
      var includeSkus = false
      fields.foreach(f => {
        projection.append(f).append(":1,")
        if (f.startsWith("skus.")) {
          includeSkus = true
        }
      })
      if (includeSkus) {
        // required for filtering
        projection.append("skus.countries.code:1,")
      }
    } else {
      // by default hide this fields
      // @todo move to config??
      projection.append("listRank:0, categories:0, skus.season:0, skus.year:0, skus.countries.allowBackorder:0,")
        .append("skus.isRetail: 0, skus.isCloseout: 0, skus.catalogs: 0, skus.customSort: 0, ")
    }
    //projection.append("skus: { $slice: [0, 10] }")
    projection.append("}")
    projection.toString()
  }

  def save(product: Product*) : Future[WriteResult] = {
    Future {
      val productCollection = jongo.getCollection("products")
      var result: WriteResult = null
      product.map( p => result = productCollection.update(s"{_id: '${p.getId()}'}").upsert().merge(p) )
      result
    }
  }
}
