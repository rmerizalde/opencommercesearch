package org.opencommercesearch.api.service

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

import com.mongodb.{MongoClient, WriteResult}
import com.mongodb.gridfs.GridFS
import org.opencommercesearch.api.models.Product
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
  }

  def close : Unit = {
    mongo.close()
  }


  def findProduct(id: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      productCollection.findOne("{_id:#}", id).projection(projection(fields)).as(classOf[Product])
    }
  }

  def findProduct(id: String, site: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      productCollection.findOne("{_id:#, skus.catalogs:#}", id, site).projection(projection(fields)).as(classOf[Product])
    }
  }

  private def projection(fields: Seq[String]) : String = {
    val projection = new StringBuilder(128)
    projection.append("{")
    if (fields.size > 0) {
      fields.foreach(projection.append(_).append(":1,"))
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
