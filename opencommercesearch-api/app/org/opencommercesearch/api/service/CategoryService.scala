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
import play.api.Logger
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import java.util
import org.opencommercesearch.api.models.{Category, Product}
import org.opencommercesearch.api.common.{FieldList, ContentPreview}
import org.apache.solr.client.solrj.{AsyncSolrServer, SolrQuery}
import org.apache.solr.common.{SolrInputDocument, SolrDocument}
import org.opencommercesearch.api.Global._
import org.apache.commons.lang3.StringUtils
import scala.collection.mutable.HashMap
import scala.Some
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.mutable
import com.mongodb.WriteResult

/**
 * Utility class that gives category / related data and operations.
 * @param server The Solr server used to fetch data from.
 */
class CategoryService(var server: AsyncSolrServer) extends FieldList with ContentPreview {

  private val MaxTries = 3
  private val MaxResults = 50
  private val CategoryPathSeparator = "\\."
  
  def findById(id: String, preview: Boolean, fields: Option[String]) : Future[Option[Category]] = {
    val (query, hasNestedCategories) =
      withNestedCategories(id,  withCategoryCollection(withFields(new SolrQuery(), fields), preview),  fields)
    
    //TODO gsegura: once we have mongo ready, change here to call it
    server.query(query).map( response => {
      
      if (hasNestedCategories) {
        val docs = response.getResults
        val lookupMap = HashMap.empty[String, SolrDocument]
        var mainDoc :SolrDocument = null
        if (docs != null) {
            JIterableWrapper(docs).foreach(
                doc => {
                  val currentId = doc.getFieldValue("id").toString
                  lookupMap += (currentId -> doc)
                  if( id.equals(currentId) ) {
                    mainDoc = doc
                  }
                  Logger.debug("Found category " + id)
                }
            )
            if(mainDoc != null) {
                val category : Category = server.binder.getBean(classOf[Category], mainDoc)
                addNestedCategoryNames(category.childCategories, lookupMap);
                addNestedCategoryNames(category.parentCategories, lookupMap);
                Logger.debug(s"Found category [$id] - has nested child categories [$hasNestedCategories]")
                Some(category)
            } else {
              //failed to find the main doc
              None
            }
        } else {
          //solr docs were null
          None
        }
      }
      else {
          val doc = response.getResponse.get("doc").asInstanceOf[SolrDocument]
          if (doc != null) {
            Logger.debug("Found category " + id)
            Some(server.binder.getBean(classOf[Category], doc))
          } else {
            None
          }
      }
    })
  }

  def addNestedCategoryNames(categories: Option[Seq[Category]], lookupMap :HashMap[String, SolrDocument] ) = {
    for( cats <- categories) {
      cats.foreach(
        category => {
          for (id <- category.id) {
            if(lookupMap.contains(id)) {
              val solrDocument : SolrDocument =  lookupMap(id)
              //category.setName(solrDocument.getFieldValue("name").toString())
              //category.setSeoUrlToken(solrDocument.getFieldValue("seoUrlToken").toString())
              val newDoc :Category = server.binder.getBean(classOf[Category], solrDocument)
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
  
  def findByIds(ids: Seq[String], preview: Boolean, fields: Option[String]) : Future[Option[Seq[Category]]] = {
    val query = withCategoryCollection(withFields(new SolrQuery(), fields), preview)

    query.setRequestHandler(RealTimeRequestHandler)
    ids.map(id => query.add("id", id))

    Logger.debug("Query categories " + ids)
    server.query(query).map( response => {
      val docs = response.getResponse.getAll("doc").asInstanceOf[util.List[SolrDocument]]
      if (docs != null && docs.size > 0) {
        Logger.debug("Found categories " + ids)
        Some(JIterableWrapper(docs).toSeq.map(doc => {
          val category = server.binder.getBean(classOf[Category], doc)

          category
        }))
      } else {
        None
      }
    })
  }

  /**
   * Generate the category tokens to create a hierarchical facet in Solr. Each
   * token is formatted such that encodes the depth information for each node
   * that appears as part of the path, and include the hierarchy separated by
   * a common separator (depth/first level category name/second level
   * category name/etc)
   *
   * @param doc
   *            The document to set the attributes to.
   * @param product
   *            The RepositoryItem for the product item descriptor
   * @param skuCatalogAssignments
   *            If the product is belongs to a category in any of those
   *            catalogs then that category is part of the returned value.
   */
  def loadCategoryPaths(doc: SolrInputDocument, product: Product, skuCatalogAssignments: Seq[String], preview: Boolean) : Unit = {
    if (product != null) {
      var tries = 1
      while (tries <= MaxTries) {
        try {
          tries += 1
          val generator = new SkuCategoryDataGenerator(doc, product, skuCatalogAssignments, preview)
          generator.generate()
          return
        } catch {
          case ex: Exception => {
            for (id <- product.id) {
              if (tries <= MaxTries) {
                doc.removeField("category")
                doc.removeField("categoryPath")
                doc.removeField("categoryNodes")
                doc.removeField("ancestorCategoryId")
                Logger.info(s"Attempt number $tries to retrieve category paths for $id after exception '$ex - ${ex.getMessage}'")
              } else {
                Logger.error(s"Cannot load category paths for $id", ex)
              }
            }
          }
        }
      }
    }
  }

  private class SkuCategoryDataGenerator(
    val doc: SolrInputDocument,
    val product: Product,
    val skuCatalogAssignments: Seq[String],
    val preview: Boolean) {

    val Fields = Some("*")
    // use a high timeout while we block
    val Timeout = Duration(10, SECONDS)

    val hierarchyCategories = new util.ArrayList[Category]
    val tokenCache = new util.HashSet[String]
    val ancestorCache = new util.HashSet[String]
    val leafCache = new util.HashSet[String]
    lazy val keyPrefix = "category." + (if (preview) "preview." else "")

    def generate() : Unit = {
      for (productCategories <- product.categories) {
        for(productCategoryId <- productCategories) {
          // block for the moment. Not ideal but this code is used by the feed only. Feeds use a few thread (normally one)
          // to send products
          val c = Await.result(findCategoryById(productCategoryId), Timeout)

          for (category <- c) {
            if (isCategoryInCatalogs(category, skuCatalogAssignments)) {
              if (category.isRuleBased.get) {
                doc.addField("ancestorCategoryId", category.id.get)
              } else {
                loadCategoryPathsAndAncestorIds(doc, category, hierarchyCategories, skuCatalogAssignments, tokenCache, ancestorCache)
              }

              if(!category.isRuleBased.get) {
                leafCache.add(category.name.get)
              }
            }
          }
        }

        if(leafCache.size() > 0) {
          for(leaf <- JIterableWrapper(leafCache)) {
            doc.addField("categoryLeaves", leaf)
          }
        }

        val nodeCache = new util.HashSet[String]

        for(token <- new JIterableWrapper(tokenCache)){
          val splitToken = StringUtils.split(token, ".")
          if(splitToken != null && splitToken.length > 2) {
            var tokenList = util.Arrays.asList(splitToken:_*)
            tokenList = tokenList.subList(2, tokenList.size())
            if (!tokenList.isEmpty) {
              nodeCache.addAll(tokenList)
            }
          }
        }

        if(nodeCache.size() > 0) {
          nodeCache.removeAll(leafCache)

          for(node <- JIterableWrapper(nodeCache)) {
            doc.addField("categoryNodes", node)
          }
        }
      }
    }

    def findCategoryById(id: String) : Future[Option[Category]] = {
      val key = keyPrefix +  id
      val category = Cache.getAs[Category](key)

      if (category.isDefined) {
        Future.successful(category)
      } else {
        Logger.debug(s"Category $id not found in cache")
        findById(id, preview, Fields).map( category => {
          if (category.isDefined) {
            Cache.set(key, category.get, CategoryCacheTtl)
            Some(category.get)
          } else {
            None
          }
        })
      }
    }

    /**
     * Helper method to test if category is assigned to any of the given catalogs
     *
     * @param category
     *            the category to be tested
     * @param catalogs
     *            the set of categories to search in
     * @return
     */
    private def isCategoryInCatalogs(category: Category, catalogs: Seq[String]) : Boolean = {

        if (catalogs == null || catalogs.size == 0) {
            return false
        }

        for (categoryCatalogs <- category.catalogs) {
          if (categoryCatalogs != null) {
              for (categoryCatalog <- categoryCatalogs) {
                  if (catalogs.contains(categoryCatalog)) {
                      return true
                  }
              }
          }
        }
        false
    }

    /**
     * Helper method to generate the category tokens recursively
     *
     *
     * @param doc
     *            The document to set the attributes to.
     * @param category
     *            The repositoryItem of the current level
     * @param hierarchyCategories
     *            The list where we store the categories during the recursion
     * @param catalogAssignments
     *            The list of catalogs to restrict the category token generation
     */
    private def loadCategoryPathsAndAncestorIds(doc: SolrInputDocument, category: Category, hierarchyCategories: util.List[Category],
            catalogAssignments: Seq[String], tokenCache: util.Set[String], ancestorCache: util.Set[String]) : Unit = {
      if (category.parentCategories != null && category.parentCategories.isDefined && category.parentCategories.get.size > 0) {
        for (parentCategories <- category.parentCategories) {
          hierarchyCategories.add(0, category)
          for (parentCategory <- parentCategories; id <- parentCategory.id) {
            // block for the moment. Not ideal but this code is used by the feed only. Feeds use a few thread (normally one)
            // to send products
            val c = Await.result(findCategoryById(id), Timeout)

            for (category <- c) {
              loadCategoryPathsAndAncestorIds(doc, category, hierarchyCategories, catalogAssignments, tokenCache, ancestorCache)
            }
          }
          hierarchyCategories.remove(0)
        }
      } else {
          for (catalogs <- category.catalogs) {
            for(catalog <- catalogs){
              if(catalogAssignments.contains(catalog)){
                generateCategoryTokens(doc, hierarchyCategories, catalog, tokenCache)
              }
            }
          }
      }

      for (id <- category.id) {
        if (!ancestorCache.contains(id)) {
          doc.addField("ancestorCategoryId", id)
          ancestorCache.add(id)
        }
      }
    }

    /**
     * Generates category tokens into a multivalued field called category. Each
     * token has the format: depth/catalog/category 1/.../category N, For
     * example:
     *
     * 0/bcs 1/bcs/Men's Clothing 2/bcs/Men's Clothing/Men's Jackets 3/bcs/Men's
     * Clothing/Men's Jackets/Men's Casual Jacket's
     *
     * @param doc The document to set the attributes to.
     * @param hierarchyCategories The list where we store the categories
     * @param catalog The catalog to which these categories belong to.
     *
     */
    private def generateCategoryTokens(doc: SolrInputDocument, hierarchyCategories: util.List[Category], catalog: String,
                                       tokenCache: util.Set[String]) : Unit = {
      if (hierarchyCategories == null) {
        return
      }

      val builder = new StringBuilder()
      val builderIds = new StringBuilder()
      for (i <- 0 to hierarchyCategories.size) {
        builder.append(i).append(".").append(catalog).append(".")
        builderIds.append(catalog).append(".")

        for (j <- 0 to i if j < i) {

          builder.append(hierarchyCategories.get(j).name.get).append(".")
          builderIds.append(hierarchyCategories.get(j).id.get).append(".")
        }
        builder.setLength(builder.length - 1)
        builderIds.setLength(builderIds.length - 1)

        val token = builder.toString()
        if (!tokenCache.contains(token)) {
          tokenCache.add(token)
          doc.addField("category", builder.toString())
          doc.addField("categoryPath", builderIds.toString())
        }
        builder.setLength(0)
        builderIds.setLength(0)
      }
    }
  }
  
  /**
   * If the fields parameter has categories or parentCategories, generated a special query to retrieve the id plus
   * documents which parentCategories or categories are the id.
   * In any other case, create a real time query for the id specified
   */
  def withNestedCategories(id: String, query: SolrQuery, fields: Option[String]): (SolrQuery, Boolean) = {
    var hasNestedCategories : Boolean = false
    
    for (f <- fields) {
      val hasChildCategories : Boolean = fields.get.contains("childCategories")
      val hasParentCategories: Boolean = fields.get.contains("parentCategories")
      
      if(hasChildCategories && hasParentCategories) {
        query.addFilterQuery(s"id:$id OR childCategories:$id OR parentCategories:$id")
        Logger.debug("Query category with parent and child categories. Id: " + id)
        hasNestedCategories = true
      } else if(hasChildCategories) {
        query.addFilterQuery(s"id:$id OR parentCategories:$id")
        Logger.debug("Query category with child categories. Id: " + id)
        hasNestedCategories = true
      } else if(hasParentCategories) {
        query.addFilterQuery(s"id:$id OR childCategories:$id")
        Logger.debug("Query category with parent categories. Id:  " + id)
        hasNestedCategories = true
      } 
    }
    
    if(!hasNestedCategories) {
      query.setRequestHandler(RealTimeRequestHandler)
      query.set("id", id)
      Logger.debug("Query category " + id)
    } else {
      query.setRows(MaxResults)
      query.setQuery("*:*")
    }
    
    
    (query,hasNestedCategories)
  }

  /**
   * Gets data from a category from storage, plus all children up to a given level.
   * @param parentCategoryId Category id to look for.
   * @param categoryPaths List of category paths necessary to build up the taxonomy for the  given parent category id. For example: [site.cat1level1.cat2level2, site.cat3level1.cat4level2.cat5level3]
   * @param maxLevels Max level on the taxonomy to drill down. If set to 1, will return the immediate children, if set to 2 will return immediate children and their corresponding children, and so on.
   * @param fields Fields to retrieve for each category on the taxonomy. This field list is applied to the parent category and any children of it.
   * @param storage Storage used to retrieve category data.
   * @return The category instance for the given category id, plus all children up to the given level.
   */
  def getTaxonomyForCategory(parentCategoryId: String, categoryPaths: Iterable[String], maxLevels: Int, fields: Seq[String], storage : Storage[WriteResult]) : Future[Category] =  {
    val childCategoriesToLookFor = new mutable.HashSet[String]

    categoryPaths.map(categoryPath => {
      val indexOf = categoryPath.indexOf(parentCategoryId)
      if(indexOf > 0) {
        categoryPath.substring(indexOf + parentCategoryId.length).split(CategoryPathSeparator).take(maxLevels).foreach( childCategory => {
          childCategoriesToLookFor += childCategory
        })
      }
    })

    childCategoriesToLookFor += parentCategoryId

    getTaxonomy(childCategoriesToLookFor, fields, storage).map( taxonomyMap => {
      taxonomyMap(parentCategoryId)
    })
  }

  /**
   * Builds the category taxonomy for a given list of categories.
   * <p/>
   * This method hydrates each category in the given list with storage data (including information about child categories) and then builds up any relationship between the
   * categories in the list. The input list of categories may not have any relationship between them, this method will then only return a map of categories with their corresponding data.   *
   * For example, if you want to know the taxonomy for all categories, then pass this method a list of all existing categories.
   * @param categories List of categories to use for building up the taxonomy
   * @param fields List of fields to return from the storage. Notice the 'childCategories' field will be returned regardless of what the field list is.
   * @param storage Storage used to retrieve category data.
   * @return Map of category ids referencing their corresponding category data. Elements on the map are related to each other based on their taxonomy data.
   */
  def getTaxonomy(categories: Iterable[String], fields: Seq[String], storage : Storage[WriteResult]) : Future[Map[String, Category]] =  {
    val categoriesToLookFor = categories.toSet

    var fieldList = fields
    //Go to Mongo to fetch category data
    if(!fields.isEmpty) {
      fieldList :+= "childCategories"
    }

    val categoriesFuture = storage.findCategories(categories, fieldList)
    val hierarchyMap = new mutable.HashMap[String, Category].withDefaultValue(null)
    //Go over the list of categories returned by Mongo and store them in a hash
    categoriesFuture.map(categoryData => {
      categoryData.foreach( category => {
        var taxonomyNode  = hierarchyMap(category.getId())

        if(taxonomyNode == null) {
          taxonomyNode = category
        }
        else {
          //TODO: Find a more maintainable way to copy this data
          taxonomyNode.childCategories = Option(category.getChildCategories())
          taxonomyNode.name = category.name
          taxonomyNode.seoUrlToken = category.seoUrlToken
        }

        //Add this category to the map
        val childCategories = category.getChildCategories().filter(childCategory => {categoriesToLookFor.contains(childCategory.getId())}).map(childCategory => {
          val tmpNode = hierarchyMap(childCategory.getId())

          if(tmpNode == null) {
            hierarchyMap += (childCategory.getId() -> childCategory)
            childCategory
          }
          else {
            tmpNode
          }
        })

        taxonomyNode.childCategories = Some(childCategories)
        hierarchyMap += (category.getId() -> taxonomyNode)
      })

      hierarchyMap.toMap
    })
  }


}
