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

import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Execution.Implicits._
import play.modules.statsd.api.Statsd

import scala.Some
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import java.util

import org.opencommercesearch.api.{ProductFacetQuery, SingleProductQuery}
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.common.{ContentPreview, FieldList}
import org.opencommercesearch.api.models.{Category, Product}
import org.opencommercesearch.common.Context

import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.{AsyncSolrServer, SolrQuery}
import org.apache.solr.common.SolrInputDocument

import com.mongodb.WriteResult

object CategoryService {
  type Taxonomy = Map[String, Category]

  def emptyTaxonomy = Map[String, Category]()
}

/**
 * Utility class that gives category / related data and operations.
 * @param server The Solr server used to fetch data from.
 */
class CategoryService(var server: AsyncSolrServer, var storageFactory: MongoStorageFactory) extends FieldList with ContentPreview {
  import CategoryService._

  private val MaxTries = 3
  private val MaxResults = 50
  private val CategoryPathSeparator = "\\."
  private val TaxonomyCacheKey = "AllCategoryTaxonomy"
  private val CategoryKeyPrefix = "category."
  private val StatsdBuildTaxonomyGraphMetric = "api.search.internal.service.buildTaxonomyGraph"
  private val StatsdBuildBrandTaxonomyGraphMetric = "api.search.internal.service.buildBrandTaxonomyGraph"
  private val StatsdBuildProductTaxonomyGraphMetric = "api.search.internal.service.buildProductTaxonomyGraph"
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
          for(leaf <- leafCache) {
            doc.addField("categoryLeaves", leaf)
          }
        }

        val nodeCache = new util.HashSet[String]

        for(token <- tokenCache){
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

          for(node <- nodeCache) {
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
            Logger.debug(s"Category $id was found in storage and will be cached for $CategoryCacheTtl seconds")
            Some(category)
          } else {
            Logger.debug(s"Category $id was not found in storage")
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
   * @param categoryId Category id to look for.
   * @param categoryPaths List of category paths matching the given parent category. This list will be parsed to extract a list of categories that should be looked for while building the taxonomy.
   * @param maxLevels Max level on the taxonomy to drill down. If set to 1, will return the immediate children, if set to 2 will return immediate children and their corresponding children, and so on.
   * @param maxChildren Max leaf children to return. It only limits those children returned in the final level specified by maxLevels. A -1 value means all children are returned.
   * @param fields Fields to retrieve for each category on the taxonomy. This field list is applied to the parent category and any children of it.
   * @param storage Storage used to retrieve category data.
   */
  def getTaxonomyForCategory(categoryId: String, categoryPaths: Iterable[String] = Set.empty, maxLevels: Int,
                             maxChildren: Int, fields: Seq[String], storage : Storage[WriteResult])
                            (implicit context: Context) : Future[Category] =  {
    //First get the taxonomy
    val taxonomyFuture = getTaxonomy(storage, context.isPreview)

    //Now, find the category we are looking for
    taxonomyFuture map { taxonomy =>
      val category = taxonomy.get(categoryId)

      if(category != null && category.isDefined) {
        var categories = new mutable.HashSet[String]

        categoryPaths foreach { categoryPath =>
          categoryPath.split(CategoryPathSeparator).foreach(categories += _)
        }

        //Iterate over parent category children, and remove those categories that don't have stock level or exceed maxLevels/maxChildren
        val startTime = System.currentTimeMillis()
        val updatedFields = updateFields(fields)
        val prunedCategory = withParents(Category.prune(category.get, categories.toSet, maxLevels, maxChildren, updatedFields), updatedFields, taxonomy)

        Statsd.timing(StatsdPruneTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
        prunedCategory
      }
      else {
        null
      }
    }
  }

  /**
   * Helper method to populate the parent categories if field list contains parentCategories or the star.
   * The children of the given category will have no parent categories.
   *
   * @param category the category to which the parent will be set
   * @param fields is the field list. This is the same fied list use to populate each parent category
   * @param taxonomy is the taxonomy map
   * @return the given category
   */
  private def withParents(category: Category, fields: Seq[String], taxonomy: Taxonomy) : Category = {
    def copyParents(parentCategories: Seq[Category]) = parentCategories map {
      parentCategory => {
        val category = taxonomy.get(parentCategory.getId).getOrElse(parentCategory)
        val categoryCopy = Category.copyCategory(category, fields, Seq("childCategories"))
        categoryCopy.parentCategories = getParents(categoryCopy)
        categoryCopy
      }
    }
    def getParents(category: Category) : Option[Seq[Category]] = category.parentCategories match {
      case Some(parentCategories) => Some(copyParents(parentCategories))
      case None => None
    }
    def removeParents(categories: Option[Seq[Category]]): Unit = {
      for (cats <- categories) {
        cats foreach { category =>
          category.parentCategories = None
          removeParents(category.childCategories)
        }
      }
    }

    if (fields.contains("parentCategories") || fields.contains("*")) {
      category.parentCategories = getParents(category)
      removeParents(category.childCategories)
    }
    category
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
  def getTaxonomy(storage : Storage[WriteResult], preview : Boolean = false) : Future[Taxonomy] =  {
    //If returning complete taxonomy, see if we already have it on cache.
    val taxonomyCacheKey = TaxonomyCacheKey + getPreviewKeyPrefix(preview)
    val cachedTaxonomy = Cache.get(taxonomyCacheKey)
    if(cachedTaxonomy.isEmpty) {
      Logger.debug("Generating taxonomy graph")
      //Go to storage and get all existing categories. Then calculate taxonomy from them.
      val startTime = System.currentTimeMillis()
      val taxonomyFuture = buildTaxonomyGraph(storage.findAllCategories(Seq("*")))
      Statsd.timing(StatsdBuildTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
      taxonomyFuture map { taxonomy =>
        val markedTaxonomy = markRootCategories(taxonomy, preview)
        Cache.set(taxonomyCacheKey, markedTaxonomy, CategoryCacheTtl)
        markedTaxonomy.toMap
      }
    }
    else {
      Logger.debug("Using cached taxonomy graph")
      Future(cachedTaxonomy.get.asInstanceOf[Taxonomy])
    }
  }

  /**
   * Scans the taxonomy to find the root nodes. Root nodes are inserted into the map with the site id as key.
   *
   * @todo code should assume sites have single root category
   * @param taxonomy is the taxonomy to scan for roots
   * @param preview Whether or not reading preview categories.
   */
  private def markRootCategories(taxonomy: Taxonomy, preview: Boolean): Taxonomy = {
    var result = emptyTaxonomy
    taxonomy foreach { case (key, category) =>
      //Warm category cache as needed
      val categoryKey = CategoryKeyPrefix + getPreviewKeyPrefix(preview) + key
      val cachedCategory = Cache.get(categoryKey)
      if(cachedCategory == null) {
        //Cached category expired, so re-add it to the cache.
        Cache.set(categoryKey, category, CategoryCacheTtl)
      }

      //Check if is a root cat
      if(isRoot(category)) {
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
              result += (key -> category)
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
   * Helper method to update the field list if childCategories is not present
   *
   * @todo follow up with Javier on why we need to add childCategories by default
   *
   * @param fields is the field list to check
   * @return the update field list. If the field list was not update returns the original list
   */
  private def updateFields(fields: Seq[String]) : Seq[String] = fields match {
    case Nil => Category.defaultFields :+ "childCategories"
    case _ => fields
  }

  /**
   * Builds category taxonomy for the given category data. Iterates over the list building up any parent children relationship found. The result is a map
   * that contains all category nodes, connected between them.
   * <p/>
   * For example, to retrieve the taxonomy for a given category. Simply look it up in the map. The returning Category instance will have all the found taxonomy data.
   * @param categoryFuture the future for the categories to build the taxonomy.
   * @param filterCategories is the list of categories used to trim the response. Can be empty (no trimming will occur).
   * @return Map of category ids referencing their corresponding category data. Elements on the map are related to each other based on their taxonomy data.
   */
  private def buildTaxonomyGraph(categoryFuture: Future[Iterable[Category]], includeParents: Boolean = true, filterCategories: Set[String] = Set.empty) : Future[Taxonomy] =  {
    categoryFuture map { categories =>
      def cleanParents(category: Category) = {
        if (!includeParents) {
          category.parentCategories = None
        }
        category
      }

      val addTaxonomyEntry = (taxonomy: Taxonomy, category: Category) => taxonomy + (category.getId -> cleanParents(category))
      val map = categories.foldLeft(emptyTaxonomy)(addTaxonomyEntry) withDefaultValue null

      map.values foreach { category =>
        category.childCategories = category.childCategories map { childCategories =>
          childCategories withFilter { childCategory =>
            filterCategories.size == 0 || filterCategories.contains(childCategory.getId)
          } map { childCategory =>
            map(childCategory.getId)
          }
        }
      }
      map
    }
  }

  /**
   * Returns a brand taxonomy.
   * @param id is the brand id
   * @param site is the site to limit the results
   * @param fields is the category field list
   * @param context is the request context
   * @return a sequence of the root categories
   */
  def getBrandTaxonomy(id: String, site: String, fields: Seq[String])(implicit context: Context): Future[Seq[Category]] = {
    val startTime = System.currentTimeMillis()
    val categoryPathQuery = new ProductFacetQuery("ancestorCategoryId", site)(context, null)
      .withBrand(id)
    categoryPathQuery.setFacetLimit(MaxFacetPaginationLimit)

    Logger.debug(s"Getting taxonomy for brand $id with query ${categoryPathQuery.toString}")

    solrServer.query(categoryPathQuery) flatMap { response =>
      val storage = withNamespace(storageFactory)
      getTaxonomy(storage, context.isPreview) map { taxonomy =>
        val facetFields = response.getFacetFields
        var rootCategories: Seq[Category] = Seq.empty

        if (facetFields != null) {
          facetFields collectFirst {
            case f if "ancestorCategoryId".equals(f.getName) => f
          } map { facet =>
            val categoryIds = facet.getValues.map(facetValue => facetValue.getName)
            Logger.debug(s"Got ${facet.getValueCount} category ids for brand $id")
            Logger.debug(s"Category ids for brand $id are $categoryIds")

            rootCategories = getRoots(taxonomy, Set(site), updateFields(fields), categoryIds.toSet)
          }
        } else {
          Logger.debug(s"Cannot retrieve ancestor category ids for product $id for site $site")
        }
        Statsd.timing(StatsdBuildBrandTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
        rootCategories
      }
    }
  }

  /**
   * Helper method to find the root categories. The results are limited to the given category ids
   * @param taxonomy is the category taxonomy
   * @param sites is the list of sites
   * @param fields is the category field list
   * @param categoryIds is the list of category ids to limit the response
   * @return a sequence of the root categories found in the given category id list
   */
  private def getRoots(taxonomy: Taxonomy, sites: Set[String], fields: Seq[String], categoryIds: Set[String]): Seq[Category] = {
    def copy(category: Category): Category = {
      val categoryCopy = Category.copyWithFields(category, fields, Seq("parentCategories"))
      categoryCopy.childCategories match {
        case Some(childCategories) =>
          categoryCopy.childCategories = Some(childCategories withFilter { childCategory =>
            categoryIds contains childCategory.getId
          } map { childCategory =>
              copy(childCategory)
          })
        case None => None
      }
      categoryCopy
    }

    sites.map(site => copy(taxonomy(site))).toSeq
  }

  /**
   * Checks if the given category is a root
   * @param category the category to check
   * @return true if category is a root, otherwise returns false
   */
  private def isRoot(category: Category) =
    category.parentCategories == null || category.parentCategories.isEmpty || category.parentCategories.get.isEmpty

  /**
   * Helper method to find the sites for the given list of category ids
   * @param categoryIds is the list of category ids to search
   * @param taxonomy is the category taxonomy
   * @return a set of sites for the given category ids
   */
  private def findSites(categoryIds: Set[String], taxonomy: Taxonomy) = {
    var sites = Set[String]()
    categoryIds foreach { categoryId =>
      taxonomy get categoryId filter { category => isRoot(category) } map { category =>
        category.sites map { categorySites =>
          sites = sites ++ categorySites
        }
      }
    }
    sites
  }

  /**
   * Return the product taxonomy.
   * @param id is the product id
   * @param site is the site to limit the results. If null, all sites are included
   * @param fields is the category field list
   * @param context is the request context
   * @return a sequence of the root categories
   */
  def getProductTaxonomy(id: String, site: String, fields: Seq[String])(implicit context: Context): Future[Seq[Category]] = {
    val startTime = System.currentTimeMillis()
    val productQuery = new SingleProductQuery(id, site)
      .withFields("ancestorCategoryId")

    Logger.debug(s"Getting taxonomy for product $id with query ${productQuery.toString}")

    solrServer.query(productQuery) flatMap { response =>
      val storage = withNamespace(storageFactory)
      getTaxonomy(storage, context.isPreview) map { taxonomy =>
        val docs = response.getResults
        var rootCategories = Seq.empty[Category]

        if (docs != null && docs.size() > 0) {
          val doc = docs.get(0)
          if (doc.containsKey("ancestorCategoryId")) {
            val categoryIds = doc.getFieldValues("ancestorCategoryId").asInstanceOf[java.util.Collection[String]].toSet
            Logger.debug(s"Got ${categoryIds.size} category ids for brand $id")
            Logger.debug(s"Category ids for brand $id are $categoryIds")
            val sites = if (site == null) findSites(categoryIds, taxonomy) else Set(site)

            rootCategories = getRoots(taxonomy, sites, updateFields(fields), categoryIds)
          } else {
            Logger.debug(s"Cannot retrieve category paths for product $id for site $site")
          }
        } else {
          Logger.debug(s"Category paths for product $id not found for site $site")
        }
        Statsd.timing(StatsdBuildProductTaxonomyGraphMetric, System.currentTimeMillis() - startTime)
        rootCategories
      }
    }
  }
}
