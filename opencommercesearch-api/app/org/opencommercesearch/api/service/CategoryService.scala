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
import org.opencommercesearch.common.Context
import org.opencommercesearch.api.models.{Category, Product}
import org.opencommercesearch.api.common.{FieldList, ContentPreview}
import org.apache.solr.client.solrj.{AsyncSolrServer, SolrQuery}
import org.apache.solr.common.{SolrInputDocument, SolrDocument}
import org.opencommercesearch.api.Global._
import org.apache.commons.lang3.StringUtils
import scala.Some
import scala.collection.convert.Wrappers.JIterableWrapper
import scala.collection.mutable
import com.mongodb.WriteResult
import play.modules.statsd.api.Statsd

/**
 * Utility class that gives category / related data and operations.
 * @param server The Solr server used to fetch data from.
 */
class CategoryService(var server: AsyncSolrServer) extends FieldList with ContentPreview {

  private val MaxTries = 3
  private val MaxResults = 50
  private val CategoryPathSeparator = "\\."
  private val TaxonomyCacheKey = "AllCategoryTaxonomy"
  private val CategoryKeyPrefix = "category."
  private val StatsdBuildTaxonomyGraphMetric = "api.search.internal.service.buildTaxonomyGraph"
  private val StatsdPruneTaxonomyGraphMetric = "api.search.internal.service.pruneTaxonomyGraph"

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
  def loadCategoryPaths(doc: SolrInputDocument, product: Product, skuCatalogAssignments: Seq[String])
                       (implicit context: Context) : Unit = {
    if (product != null) {
      var tries = 1
      while (tries <= MaxTries) {
        try {
          tries += 1
          val generator = new SkuCategoryDataGenerator(doc, product, skuCatalogAssignments, context)
          generator.generate()
          return
        } catch {
          case ex: Exception =>
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

  /**
   * Gets the proper cache key prefix for preview collections
   * @param preview Whether or not currently using a preview collection.
   */
  private def getPreviewKeyPrefix(preview: Boolean) : String = {
    if (preview) "preview." else ""
  }

  private class SkuCategoryDataGenerator(
                                          val doc: SolrInputDocument,
                                          val product: Product,
                                          val skuCatalogAssignments: Seq[String],
                                          implicit val context: Context) {
    val Fields = Seq("*")
    // use a high timeout while we block
    val Timeout = Duration(10, SECONDS)

    val hierarchyCategories = new util.ArrayList[Category]
    val tokenCache = new util.HashSet[String]
    val ancestorCache = new util.HashSet[String]
    val leafCache = new util.HashSet[String]
    lazy val keyPrefix = CategoryKeyPrefix + getPreviewKeyPrefix(context.isPreview)

    def generate() : Unit = {
      Logger.debug("Generating category data for product " + product.getId)
      for (productCategories <- product.categories) {
        Logger.debug(s"Categories for product ${product.getId}: ${productCategories.map(c => c.getId)}")
        for(productCategory <- productCategories) {
          // block for the moment. Not ideal but this code is used by the feed only. Feeds use a few thread (normally one)
          // to send products
          val c = Await.result(findCategoryById(productCategory.getId), Timeout)

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

    def findCategoryById(id: String)(implicit context: Context) : Future[Option[Category]] = {
      val key = keyPrefix +  id
      val category = Cache.getAs[Category](key)

      if (category.isDefined) {
        Future.successful(category)
      } else {
        Logger.debug(s"Category $id not found in cache")
        val storage = withNamespace(storageFactory)

        storage.findCategory(id, Fields) map { category =>
          if (category != null) {
            Cache.set(key, category, CategoryCacheTtl)
            Some(category)
          } else {
            None
          }
        }
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
      Logger.debug(s"Checking if category ${category.getId} is in catalogs '$catalogs'")

      if (catalogs == null || catalogs.size == 0) {
        return false
      }

      for (categorySites <- category.sites) {
        if (categorySites != null) {
          for (categorySite <- categorySites) {
            if (catalogs.contains(categorySite)) {
              Logger.debug(s"Category ${category.getId} is assigned to catalog '$categorySite'")
              return true
            }
          }
        }
      }
      Logger.debug(s"Category ${category.getId} is not in catalogs '$skuCatalogAssignments'")
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
        for (sites <- category.sites) {
          for(site <- sites){
            if(catalogAssignments.contains(site)){
              generateCategoryTokens(doc, hierarchyCategories, site, tokenCache)
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
   * If the fields parameter has categories or parentCategories, generate a special query to retrieve the id plus
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
   * @param categoryPaths List of category paths matching the given parent category. This list will be parsed to extract a list of categories that should be looked for while building the taxonomy.
   * @param maxLevels Max level on the taxonomy to drill down. If set to 1, will return the immediate children, if set to 2 will return immediate children and their corresponding children, and so on.
   * @param maxChildren Max leaf children to return. It only limits those children returned in the final level specified by maxLevels. A -1 value means all children are returned.
   * @param fields Fields to retrieve for each category on the taxonomy. This field list is applied to the parent category and any children of it.
   * @param storage Storage used to retrieve category data.
   */
  def getTaxonomyForCategory(parentCategoryId: String, categoryPaths: Iterable[String] = Set.empty, maxLevels: Int,
                             maxChildren: Int, fields: Seq[String], storage : Storage[WriteResult])
                            (implicit context: Context) : Future[Category] =  {
    //First get the taxonomy
    val taxonomyFuture = getTaxonomy(storage, context.isPreview)

    //Now, find the category we are looking for
    taxonomyFuture map { taxonomy =>
      val parentCategory = taxonomy.get(parentCategoryId)

      if(parentCategory != null && parentCategory.isDefined) {
        var categories = new mutable.HashSet[String]

        categoryPaths foreach { categoryPath =>
          categoryPath.split(CategoryPathSeparator).foreach(categories += _)
        }

        //Iterate over parent category children, and remove those categories that don't have stock level or exceed maxLevels/maxChildren
        val startTime = System.currentTimeMillis()
        val result = Category.prune(parentCategory.get, categories.toSet, maxLevels, maxChildren, addChildCategoriesField(fields))

        Statsd.timing(StatsdPruneTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
        result
      }
      else {
        null
      }
    }
  }

  /**
   * Builds the taxonomy for all existing categories.
   * <p/>
   * This method will first try to find a cached version of the taxonomy. Only if not present the entire taxonomy is computed.
   * <p/>
   * By default, all available category fields are loaded.
   * @param storage Storage used to look for category data.
   * @return Map of all existing category ids referencing their corresponding category data. Elements on the map are related to each other based on their taxonomy data.
   */
  def getTaxonomy(storage : Storage[WriteResult], preview : Boolean = false) : Future[Map[String, Category]] =  {
    //If returning complete taxonomy, see if we already have it on cache.
    val taxonomyCacheKey = TaxonomyCacheKey + getPreviewKeyPrefix(preview)
    val cachedTaxonomy = Cache.get(taxonomyCacheKey)
    if(cachedTaxonomy.isEmpty) {
      Logger.debug("Generating taxonomy graph")
      //Go to storage and get all existing categories. Then calculate taxonomy from them.
      val startTime = System.currentTimeMillis()
      val taxonomyFuture = buildTaxonomyGraph(storage.findAllCategories(Seq({"*"})))
      Statsd.timing(StatsdBuildTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
      taxonomyFuture map { taxonomy =>
        val markedTaxonomy = markRootCategories(taxonomy, preview)
        Cache.set(taxonomyCacheKey, markedTaxonomy, CategoryCacheTtl)
        markedTaxonomy.toMap
      }
    }
    else {
      Logger.debug("Using cached taxonomy graph")
      Future(cachedTaxonomy.get.asInstanceOf[mutable.HashMap[String, Category]].toMap)
    }
  }

  /**
   * Marks all root categories by scanning the provided taxonomy. Parent categories are removed from the map, and then added back with the site/catalog the belong
   * to as key. This is necessary if you want, for example, retrieve all top level categories for a given site.
   * @param taxonomy The taxonomy where root categories will be marked.
   * @param preview Whether or not reading preview categories.
   */
  private def markRootCategories(taxonomy: mutable.Map[String, Category], preview: Boolean) : mutable.Map[String, Category] = {
    var result = new mutable.HashMap[String, Category]()
    taxonomy foreach { case (key, category) =>
      //Warm category cache as needed
      val categoryKey = CategoryKeyPrefix + getPreviewKeyPrefix(preview) + key
      val cachedCategory = Cache.get(categoryKey)
      if(cachedCategory == null) {
        //Cached category expired, so re-add it to the cache.
        Cache.set(categoryKey, category, CategoryCacheTtl)
      }

      //Check if is a root cat
      if(category.parentCategories == null || category.parentCategories.isEmpty || category.parentCategories.get.isEmpty) {
        //Find out if there are catalog assignments
        for(sites <- category.sites) {
          if(!sites.isEmpty) {
            val site = category.sites.get(0)
            if(result.contains(site)) {
              Logger.error(s"Found a duplicate root category node. Previous id was ${category.getId}, new id is $key. Assuming the first one found is correct, however " +
                s"this is an indicator of taxonomy issues on storage.")
            }
            else {
              result += (site -> category)
            }
          }
        }
      }
      else {
        result += (key -> category)
      }
    }

    result
  }

  /**
   * Builds the taxonomy for a given list of categories.
   * <p/>
   * This method hydrates each category in the given list with storage data (including information about child categories) and then builds up any relationship between the
   * categories in the list. The input list of categories may not have any relationship between them, in which case this method will then only return a map of categories with their corresponding data.
   * @param categories Input list of categories used to build the taxonomy. Final graph will only have elements from this list.
   * @param fields List of fields to return. By default, 'childCategories' is added if not present.
   * @param storage Storage used to look for category data.
   * @return Mutable map of category ids referencing their corresponding category data. Elements on the map are related to each other based on their taxonomy data.
   */
  def getTaxonomy(categories: Iterable[String], fields: Seq[String], storage : Storage[WriteResult]) : Future[mutable.Map[String, Category]] =  {
    //Go to storage and get data for the given categories. Then calculate taxonomy from them.
    val startTime = System.currentTimeMillis()
    val taxonomy = buildTaxonomyGraph(storage.findCategories(categories, addChildCategoriesField(fields)), categories.toSet)
    Statsd.timing(StatsdBuildTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
    taxonomy
  }

  /**
   * Helper method that adds the child categories field if needed to a given field list.
   * @param fields List of fields to check for.
   * @return List of fields
   */
  private def addChildCategoriesField(fields: Seq[String]) : Seq[String] = {
    if(!fields.contains("childCategories")) {
      var newFields = fields
      //If fields was empty, then include other default fields.
      if(newFields.isEmpty) {
        newFields ++= Category.defaultFields
      }

      newFields :+ "childCategories"
    }
    else {
      fields
    }
  }

  /**
   * Builds category taxonomy for the given category data. Iterates over the list building up any parent children relationship found. The result is a map
   * that contains all category nodes, connected between them.
   * <p/>
   * For example, to retrieve the taxonomy for a given category. Simply look it up in the map. The returning Category instance will have all the found taxonomy data.
   * @param categoryData Input category data necessary to build the taxonomy.
   * @param filterCategories List of categories used to trim the response. Can be empty (no trimming will occur).
   * @return Map of category ids referencing their corresponding category data. Elements on the map are related to each other based on their taxonomy data.
   */
  private def buildTaxonomyGraph(categoryData: Future[Iterable[Category]], filterCategories: Set[String] = Set.empty) : Future[mutable.Map[String, Category]] =  {
    val hierarchyMap = new mutable.HashMap[String, Category].withDefaultValue(null)
    //Go over the list of categories returned by storage and store them in a hash
    categoryData.map(categories => {
      categories.foreach( category => {
        var taxonomyNode  = hierarchyMap(category.getId)

        if(taxonomyNode == null) {
          taxonomyNode = category
        }
        else {
          //TODO: Find a more maintainable way to copy this data
          taxonomyNode.childCategories = category.childCategories
          taxonomyNode.parentCategories = category.parentCategories
          taxonomyNode.isRuleBased = category.isRuleBased
          taxonomyNode.sites = category.sites
          taxonomyNode.name = category.name
          taxonomyNode.seoUrlToken = category.seoUrlToken
        }

        //Add this category to the map
        val childCategories = category.childCategories.getOrElse(Seq.empty).filter(childCategory => {filterCategories.size == 0 || filterCategories.contains(childCategory.getId)}).map(childCategory => {
          val tmpNode = hierarchyMap(childCategory.getId)

          if(tmpNode == null) {
            hierarchyMap += (childCategory.getId -> childCategory)
            childCategory
          }
          else {
            tmpNode
          }
        })

        taxonomyNode.childCategories = Some(childCategories)
        hierarchyMap += (category.getId -> taxonomyNode)
      })

      hierarchyMap
    })
  }
}
