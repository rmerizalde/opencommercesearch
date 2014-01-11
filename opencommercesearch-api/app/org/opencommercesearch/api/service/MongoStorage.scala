package org.opencommercesearch.api.service

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.collection.JavaConversions._

import com.mongodb.{MongoClient, WriteResult}
import com.mongodb.gridfs.GridFS
import org.opencommercesearch.api.models.Product
import org.jongo.Jongo
import play.api.Logger
import org.opencommercesearch.api.models.Category
import scala.collection.mutable.HashMap
import scala.collection.convert.Wrappers.JIterableWrapper
import org.opencommercesearch.api.models.Brand

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
    //TODO: add the corresponding indexes for categories and brands collections
  }

  def close : Unit = {
    mongo.close()
  }


  def findProduct(id: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      productCollection.findOne("{_id:#}", id).projection(projectionProduct(fields)).as(classOf[Product])
    }
  }

  def findProduct(id: String, site: String, fields: Seq[String]) : Future[Product] = {
    Future {
      val productCollection = jongo.getCollection("products")
      productCollection.findOne("{_id:#, skus.catalogs:#}", id, site).projection(projectionProduct(fields)).as(classOf[Product])
    }
  }

  
  def saveProduct(product: Product*) : Future[WriteResult] = {
    Future {
      val productCollection = jongo.getCollection("products")
      var result: WriteResult = null
      product.map( p => result = productCollection.update(s"{_id: '${p.getId()}'}").upsert().merge(p) )
      result
    }
  }
  
  
  def saveCategory(category: Category*) : Future[WriteResult] = {
    Future {
      val categoryCollection = jongo.getCollection("categories")
      var result: WriteResult = null
      category.map( c => result = categoryCollection.update(s"{_id: '${c.getId()}'}").upsert().merge(c) )
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
  
  def saveBrand(brand: Brand*) : Future[WriteResult] = {
    Future {
      val brandCollection = jongo.getCollection("brands")
      var result: WriteResult = null
      brand.map( b => result = brandCollection.update(s"{_id: '${b.getId()}'}").upsert().merge(b) )
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
  
  
  
  
  private def mergeNestedCategories(id: String, categories : java.lang.Iterable[Category]) : Category = {
    val lookupMap = HashMap.empty[String, Category]
        var mainDoc :Category = null;
        if (categories != null) {
            JIterableWrapper(categories).foreach(
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
                addNestedCategoryNames(mainDoc.childCategories, lookupMap);
                addNestedCategoryNames(mainDoc.parentCategories, lookupMap);
            } 
        }
        mainDoc
  }
  
  private def addNestedCategoryNames(categories: Option[Seq[Category]], lookupMap :HashMap[String, Category] ) = {
    for( cats <- categories) {
      cats.foreach(
        category => {
          for (id <- category.id) {
            if(lookupMap.contains(id)) {
              val newDoc : Category =  lookupMap(id)
              category.name = newDoc.name
              category.seoUrlToken = newDoc.seoUrlToken
              category.catalogs = newDoc.catalogs
            } else {
              Logger.error(s"Missing nested category id reference [$id]")
            }
          }
        }
      )
    }
  }
  
  
  private def projection(fields: Seq[String], defaultFieldsToHide: String) : String = {
    val projection = new StringBuilder(128)
    projection.append("{")
    if (fields.size > 0) {
      fields.foreach(projection.append(_).append(":1,"))
    } else {
      // by default hide this fields
      // @todo move to config??
      projection.append(defaultFieldsToHide)
    }
    //projection.append("skus: { $slice: [0, 10] }")
    projection.append("}")
    projection.toString()
  }
  
  private def projectionCategory(fields: Seq[String]) : String = {    
    projection(fields, "childCategories:0, parentCategories:0, isRuleBased:0, catalogs:0")
  }
  
  private def projectionProduct(fields: Seq[String]) : String = {    
    projection(fields, "listRank:0, categories:0, skus.season:0, skus.year:0, skus.countries.allowBackorder:0,skus.isRetail: 0, skus.isCloseout: 0, skus.catalogs: 0, skus.customSort: 0")
  }
  
  private def projectionBrand(fields: Seq[String]) : String = {    
    projection(fields, "logo:0, url:0")
  }

}
