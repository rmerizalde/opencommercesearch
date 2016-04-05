package org.opencommercesearch.api.service

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License") you may not use this file except in compliance
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

import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.apache.solr.client.solrj.response.{FacetField, QueryResponse}
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.SolrParams
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.mockito.Mockito.doReturn
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.ProductFacetQuery
import org.opencommercesearch.api.models.{Category, Product}
import org.opencommercesearch.common.Context
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import play.api.Logger
import play.api.i18n.Lang
import play.api.test.FakeApplication
import play.api.test.Helpers._
import reactivemongo.core.commands.LastError
import collection.JavaConversions._

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import play.api.cache.{EhCachePlugin}
import play.api.Play.current

import scala.util.{Success, Failure}

@RunWith(classOf[JUnitRunner])
class CategoryServiceSpec extends Specification with Mockito {
  private val catalogOutdoor:String = "outdoorCatalog"
  private val catRoot = mock[Category]
  private val catRulesBased = mock[Category]
  private val catShoesFootwear = mock[Category]
  private val catMensShoesBoots = mock[Category]
  private val catMensRainBootsShoes = mock[Category]
  private val catMensRainShoes = mock[Category]
  private val catMensRainBoots = mock[Category]
  private val catMensClothing = mock[Category]
  private val catMensShoesFootwear = mock[Category]
  private val catSnowshoe = mock[Category]
  private val catSnowshoeAccessories = mock[Category]
  private val catSnowshoeFootwear = mock[Category]
  private val catSnowshoeBoots = mock[Category]
  private val categoryCatalogs = Seq(catalogOutdoor)
  private val lang = new Lang("en", "US")

  val otherCatalog = "otherCatalog"
  val rootOtherCategory = mock[Category]
  val otherCategory = mock[Category]
  var taxonomy: CategoryService.Taxonomy = null
  var queryResponse = mock[QueryResponse]

  private def setup() : Unit = {
    // Root
    mockCategory(catRoot, "catRoot", "root", categoryCatalogs, null,
      newSet(catRulesBased, catShoesFootwear, catMensClothing, catSnowshoe), isRuleBased = false)
    // Rules Based
    mockCategory(catRulesBased, "catRulesBased", "Rules Based", categoryCatalogs, newSet(catRoot), null, isRuleBased = true)
    // Shoes & Footwear
    mockCategory(catShoesFootwear, "outdoorCat4000003", "Shoes & Footwear", categoryCatalogs, newSet(catRoot),
      newSet(catMensShoesBoots), isRuleBased = false)
    // Men's Shoes & Boots
    mockCategory(catMensShoesBoots, "outdoorCat4100004", "Men's Shoes & Boots", categoryCatalogs, newSet(catShoesFootwear),
      newSet(catMensRainBootsShoes), isRuleBased = false)
    // Men's Clothing
    mockCategory(catMensClothing, "outdoorCat100003", "Men's Clothing", categoryCatalogs, newSet(catRoot), newSet(catMensShoesFootwear), isRuleBased = false)
    // Men's Shoes & Footwear
    mockCategory(catMensShoesFootwear, "outdoorCat11000219", "Men's Shoes & Footwear", categoryCatalogs, newSet(catMensClothing),
      newSet(catMensRainBootsShoes), isRuleBased = false)
    // Men's Rain Boots & Shoes
    mockCategory(catMensRainBootsShoes, "outdoorCat41100024", "Men's Rain Boots & Shoes", categoryCatalogs,
      newSet(catMensShoesBoots, catMensShoesFootwear), newSet(catMensRainShoes, catMensRainBoots), isRuleBased = false)
    // Men's Rain Shoes
    mockCategory(catMensRainShoes, "outdoorCat41110026", "Men's Rain Shoes", categoryCatalogs, newSet(catMensRainBootsShoes), null, isRuleBased = false)
    // Men's Rain Boots
    mockCategory(catMensRainBoots, "outdoorCat41110025", "Men's Rain Boots", categoryCatalogs, newSet(catMensRainBootsShoes), null, isRuleBased = false)
    // Snowshoe
    mockCategory(catSnowshoe, "outdoorCat11000003", "Snowshoe", categoryCatalogs, newSet(catRoot), newSet(catSnowshoeAccessories), isRuleBased = false)
    // Snowshoe Accessories
    mockCategory(catSnowshoeAccessories, "outdoorCat111000028", "Snowshoe Accessories", categoryCatalogs, newSet(catSnowshoe),
      newSet(catSnowshoeFootwear), isRuleBased = false)
    // Snowshoe Footwear
    mockCategory(catSnowshoeFootwear, "outdoorCat111100030", "Snowshoe Footwear", categoryCatalogs, newSet(catSnowshoeAccessories),
      newSet(catSnowshoeBoots), isRuleBased = false)
    // Snowshoe boots
    mockCategory(catSnowshoeBoots, "outdoorCat111110031", "Snowshoe Boots", categoryCatalogs, newSet(catSnowshoeFootwear), null, isRuleBased = false)

    mockCategory(rootOtherCategory, "rootOtherCategory", "Root Other Category", Seq(otherCatalog), null, newSet(otherCategory), isRuleBased = false)
    mockCategory(otherCategory, "otherCategory", "Other Category", Seq(otherCatalog), newSet(rootOtherCategory), null, isRuleBased = false)

  }

  val categoryChildMap = Map(
    "catRoot" -> Seq("catRulesBased", "outdoorCat4000003", "outdoorCat100003", "outdoorCat11000003"),
    "catRulesBased" -> Seq.empty[String],
    "outdoorCat4000003" -> Seq("outdoorCat4100004"),
    "outdoorCat4100004" -> Seq("outdoorCat41100024"),
    "outdoorCat100003" -> Seq("outdoorCat11000219"),
    "outdoorCat11000219" -> Seq("outdoorCat41100024"),
    "outdoorCat41100024" -> Seq("outdoorCat41110026", "outdoorCat41110025"),
    "outdoorCat41110026" -> Seq.empty[String],
    "outdoorCat41110025" -> Seq.empty[String],
    "outdoorCat11000003" -> Seq("outdoorCat111000028"),
    "outdoorCat111000028" -> Seq("outdoorCat111100030"),
    "outdoorCat111100030" -> Seq("outdoorCat111110031"),
    "outdoorCat111110031" -> Seq.empty[String],
    "rootOtherCategory" -> Seq("otherCategory"),
    "otherCategory" -> Seq.empty[String]
  )

  val categoryMap = Map(
    "catRoot" -> catRoot,
    "catRulesBased" -> catRulesBased,
    "outdoorCat4000003" -> catShoesFootwear,
    "outdoorCat4100004" -> catMensShoesBoots,
    "outdoorCat100003" -> catMensClothing,
    "outdoorCat11000219" -> catMensShoesFootwear,
    "outdoorCat41100024" -> catMensRainBootsShoes,
    "outdoorCat41110026" -> catMensRainShoes,
    "outdoorCat41110025" -> catMensRainBoots,
    "outdoorCat11000003" -> catSnowshoe,
    "outdoorCat111000028" -> catSnowshoeAccessories,
    "outdoorCat111100030" -> catSnowshoeFootwear,
    "outdoorCat111110031" -> catSnowshoeBoots,
    "rootOtherCategory" -> rootOtherCategory,
    "otherCategory" -> otherCategory)

  private def setupService() : (CategoryService, MongoStorage) = {
    val server = mock[AsyncSolrServer]
    server.binder returns mock[DocumentObjectBinder]
    val queryResponse2 = mock[QueryResponse]
    queryResponse = queryResponse2
    server.query(any[SolrQuery]) answers { q => {
      Future.successful(queryResponse2)
    }}
    val storage = mock[MongoStorage]
    val storageFactory = mock[MongoStorageFactory]
    val service = spy(new CategoryService(server, storageFactory))

    taxonomy = categoryMap
    storage.findCategory(any[String], any[Seq[String]]) answers { id =>
      Future.successful(categoryMap.get(id.asInstanceOf[String]).get)
    }

    storage.findAllCategories(any[Seq[String]]) answers { fields =>
      Future.successful({
        categoryMap.values
      })
    }

    doReturn(storage).when(service).withNamespace(any[StorageFactory[LastError]])(any[Context])
    (service, storage)
  }

  private def mockCategory(category: Category, categoryId: String, displayName: String, categoryCatalogs: Seq[String],
    parentCategories: Seq[Category], childCategories: Seq[Category], isRuleBased: Boolean) : Unit = {
    category.getId returns categoryId
    category.id returns Some(categoryId)
    category.name returns Some(displayName)
    category.sites returns Some(categoryCatalogs)
    category.getSites returns categoryCatalogs
    if (parentCategories != null) {
      category.parentCategories returns Some(parentCategories)
    } else {
      category.parentCategories returns None
    }
    if (childCategories != null) {
      category.childCategories returns Some(childCategories)
    } else {
      category.childCategories returns None
    }
    category.isRuleBased returns Some(isRuleBased)
  }

  private def newSet(items: Category*) : Seq[Category] = {
     Seq(items:_*)
  }

  sequential

  "CategoryService" should {
    setup()

    "bottom up taxonomy with assignment in both categories" in {
      running(FakeApplication()) {
        val categoryService = setupService()._1
        implicit val context = Context(true, lang)
        val output = categoryService.getBottomUpCategoryIds(taxonomy, catalogOutdoor, Set("outdoorCat41110025", "outdoorCat111110031", "catRulesBased"))
        output must contain("outdoorCat41110025")
        output must contain("outdoorCat41100024")
        output must contain("outdoorCat4100004")
        output must contain("outdoorCat11000219")
        output must contain("outdoorCat100003")
        output must contain("outdoorCat4000003")
        
        output must contain("outdoorCat11000003")
        output must contain("outdoorCat111110031")
        output must contain("outdoorCat111100030")
        output must contain("outdoorCat111000028")
        
        output must not contain("catRulesBased") //exclude rule based categories
        
        output must contain("catRoot")
        
        output must have size(11)
      }
    }
    
    "bottom up taxonomy with assignment in one category" in {
      running(FakeApplication()) {
        val categoryService = setupService()._1
        implicit val context = Context(true, lang)
        val output = categoryService.getBottomUpCategoryIds(taxonomy, catalogOutdoor, Set("outdoorCat41110025"))
        output must contain("outdoorCat41110025")
        output must contain("outdoorCat41100024")
        output must contain("outdoorCat4100004")
        output must contain("outdoorCat11000219")
        output must contain("outdoorCat100003")
        output must contain("outdoorCat4000003")
        
        output must not contain("outdoorCat11000003")
        output must not contain("outdoorCat111110031")
        output must not contain("outdoorCat111100030")
        output must not contain("outdoorCat111000028")
        
        output must contain("catRoot")
        
        output must have size(7)
      }
    }
    
    "include rule based category for ancestor" in {
      running(FakeApplication()) {
        val product = mock[Product]
        val doc = mock[SolrInputDocument]
        val categoryService = setupService()._1
        implicit val context = Context(true, lang)

        product.id returns Some("PRD0001")
        product.categories returns Some(Seq(catSnowshoeBoots, catRulesBased))

        categoryService.loadCategoryPaths(doc, product, Seq(catalogOutdoor))

        there was one(doc).addField("category", "0.outdoorCatalog")
        there was one(doc).addField("category", "1.outdoorCatalog.Snowshoe")
        there was one(doc).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories")
        there was one(doc).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear")
        there was one(doc).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots")
        there was atMost(5)(doc).addField(Matchers.eq("category"), any[String])

        there was one(doc).addField("categoryNodes", "Snowshoe")
        there was one(doc).addField("categoryNodes", "Snowshoe Accessories")
        there was one(doc).addField("categoryNodes", "Snowshoe Footwear")
        there was atMost(3)(doc).addField(Matchers.eq("categoryNodes"), any[String])
        there was one(doc).addField("categoryLeaves", "Snowshoe Boots")
        there was atMost(1)(doc).addField(Matchers.eq("categoryLeaves"), any[String])

        there was one(doc).addField("categoryPath", "outdoorCatalog")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031")
        there was atMost(5)(doc).addField(Matchers.eq("categoryPath"), any[String])

        there was one(doc).addField("ancestorCategoryId", "catRoot")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat11000003")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111000028")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111100030")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111110031")
        there was atMost(6)(doc).addField(Matchers.eq("ancestorCategoryId"), any[String])

        // for rule based categories we only index the ancestor id. This is to support hand pick rules.
        there was no(doc).addField("category", "1.outdoorCatalog.Rules Based")
        there was no(doc).addField("categoryPath", "outdoorCatalog.catRulesBased")
        there was no(doc).addField("categoryLeaves", "Rules Based")
        there was one(doc).addField("ancestorCategoryId", "catRulesBased")
      }
    }

    "should not include duplicates" in {
      running(FakeApplication()) {
        val product = mock[Product]
        val doc = mock[SolrInputDocument]
        val categoryService = setupService()._1
        implicit val context = Context(true, lang)

        product.id returns Some("PRD0001")
        product.categories returns Some(Seq(catMensRainShoes, catMensRainBoots, catSnowshoeBoots))
        categoryService.loadCategoryPaths(doc, product, Seq(catalogOutdoor))
        
        there was one(doc).addField("category", "0.outdoorCatalog")
        there was one(doc).addField("category", "1.outdoorCatalog.Shoes & Footwear")
        there was one(doc).addField("category", "2.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots")
        there was one(doc).addField("category", "3.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes")
        there was one(doc).addField("category", "4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Shoes")
        there was one(doc).addField("category", "4.outdoorCatalog.Shoes & Footwear.Men's Shoes & Boots.Men's Rain Boots & Shoes.Men's Rain Boots")
        there was one(doc).addField("category", "1.outdoorCatalog.Men's Clothing")
        there was one(doc).addField("category", "2.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear")
        there was one(doc).addField("category", "3.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes")
        there was one(doc).addField("category", "4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Shoes")
        there was one(doc).addField("category", "4.outdoorCatalog.Men's Clothing.Men's Shoes & Footwear.Men's Rain Boots & Shoes.Men's Rain Boots")
        there was one(doc).addField("category", "1.outdoorCatalog.Snowshoe")
        there was one(doc).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories")
        there was one(doc).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear")
        there was one(doc).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots")
        there was atMost(15)(doc).addField(Matchers.eq("category"), any[String])
        
        there was one(doc).addField("categoryPath", "outdoorCatalog")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat4000003")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110026")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat4000003.outdoorCat4100004.outdoorCat41100024.outdoorCat41110025")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat100003")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110026")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat100003.outdoorCat11000219.outdoorCat41100024.outdoorCat41110025")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031")
        there was atMost(15)(doc).addField(Matchers.eq("categoryPath"), any[String])

        there was one(doc).addField("ancestorCategoryId", "catRoot")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat4000003")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat4100004")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat41100024")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat100003")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat11000003")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat41110026")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat41110025")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat11000219")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111000028")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111100030")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111110031")
        there was atMost(12)(doc).addField(Matchers.eq("ancestorCategoryId"), any[String])

        there was one(doc).addField("categoryNodes", "Men's Clothing")
        there was one(doc).addField("categoryNodes", "Men's Shoes & Boots")
        there was one(doc).addField("categoryNodes", "Snowshoe Footwear")
        there was one(doc).addField("categoryNodes", "Snowshoe Accessories")
        there was one(doc).addField("categoryNodes", "Snowshoe")
        there was one(doc).addField("categoryNodes", "Shoes & Footwear")
        there was one(doc).addField("categoryNodes", "Men's Shoes & Footwear")
        there was one(doc).addField("categoryNodes", "Men's Rain Boots & Shoes")
        there was atMost(8)(doc).addField(Matchers.eq("categoryNodes"), any[String])
        there was one(doc).addField("categoryLeaves", "Men's Rain Shoes")
        there was one(doc).addField("categoryLeaves", "Snowshoe Boots")
        there was one(doc).addField("categoryLeaves", "Men's Rain Boots")
        there was atMost(3)(doc).addField(Matchers.eq("categoryLeaves"), any[String])
      }
    }

    "should not include data from categories not in current catalog" in {
      running(FakeApplication()) {
        val product = mock[Product]
        val doc = mock[SolrInputDocument]
        val categoryService = setupService()._1
        implicit val context = Context(true, lang)

        product.id returns Some("PRD0001")
        product.categories returns Some(Seq(catSnowshoeBoots, otherCategory))
        categoryService.loadCategoryPaths(doc, product, Seq(catalogOutdoor))

        there was one(doc).addField("category", "0.outdoorCatalog")
        there was one(doc).addField("category", "1.outdoorCatalog.Snowshoe")
        there was one(doc).addField("category", "2.outdoorCatalog.Snowshoe.Snowshoe Accessories")
        there was one(doc).addField("category", "3.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear")
        there was one(doc).addField("category", "4.outdoorCatalog.Snowshoe.Snowshoe Accessories.Snowshoe Footwear.Snowshoe Boots")
        there was no(doc).addField("category", "0.otherCatalog")
        there was no(doc).addField("category", "1.otherCatalog.Other Category")
        there was atMost(5)(doc).addField(Matchers.eq("category"), any[String])

        there was one(doc).addField("categoryPath", "outdoorCatalog")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030")
        there was one(doc).addField("categoryPath", "outdoorCatalog.outdoorCat11000003.outdoorCat111000028.outdoorCat111100030.outdoorCat111110031")
        there was atMost(5)(doc).addField(Matchers.eq("categoryPath"), any[String])

        there was one(doc).addField("ancestorCategoryId", "catRoot")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat11000003")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111000028")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111100030")
        there was one(doc).addField("ancestorCategoryId", "outdoorCat111110031")
        there was no(doc).addField("ancestorCategoryId", "otherCategory")
        there was atMost(5)(doc).addField(Matchers.eq("ancestorCategoryId"), any[String])
      }
    }

    "should get the taxonomy" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled"
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {
        val (categoryService, storage) = setupService()
        implicit val context = Context(true, lang)
        val futureTaxonomy = validateTaxonomy(categoryService, storage)
        Await.result(futureTaxonomy, Duration.Inf)
        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])
      }
    }

    "should get the taxonomy when concurrent gets calling mongo only once" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled"
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {

        implicit val context = Context(true, lang)
        val (categoryService, storage) = setupService()

        val futureTaxonomyFirst = categoryService.getTaxonomy(storage, false) map { taxonomy =>
          categoryMap.keys.foreach(key => {
            taxonomy(key) must beEqualTo(categoryMap(key))
          })
          //plus two to include the 2 root catalog nodes this methods adds (outdoorCatalog & otherCatalog)
          taxonomy must have size (categoryMap.size + 2)
        }
        val futureTaxonomySecond = categoryService.getTaxonomy(storage, false) map { taxonomy =>
          categoryMap.keys.foreach(key => {
            taxonomy(key) must beEqualTo(categoryMap(key))
          })
          //plus two to include the 2 root catalog nodes this methods adds (outdoorCatalog & otherCatalog)
          taxonomy must have size (categoryMap.size + 2)
        }

        awaitSuccess(Seq(futureTaxonomyFirst, futureTaxonomySecond))

        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])

      }
    }

    "should get the taxonomy for a category Id" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled"
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {
        val (categoryService, storage) = setupService()
        implicit val context = Context(true, lang)

        val futureTaxonomy = categoryService.getTaxonomyForCategory("outdoorCat100003",
          Seq("outdoorCat11000219", "outdoorCat11000219.outdoorCat41100024", "outdoorCat11000219.outdoorCat41100024.outdoorCat41110026",
              "outdoorCat11000219.outdoorCat41100024.outdoorCat41110025"), 2, 3, Seq.empty[String], storage)(context) map {
          taxonomy => {
            val parentCat = taxonomy.get
            parentCat.getName must beEqualTo("Men's Clothing")

            val firstLevelChildCats = parentCat.childCategories.get
            firstLevelChildCats.size must beEqualTo(1)
            firstLevelChildCats.head.getName must beEqualTo("Men's Shoes & Footwear")

            val secondLevelChildCats = firstLevelChildCats.head.childCategories.get
            secondLevelChildCats.size must beEqualTo(1)
            secondLevelChildCats.head.getName must beEqualTo("Men's Rain Boots & Shoes")
            secondLevelChildCats.head.childCategories.get.size must beEqualTo(0)
          }
        }

        Await.result(futureTaxonomy, Duration.Inf)
        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])
      }
    }


    "shouldn't get the taxonomy for a invalid category id" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled"
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {
        val (categoryService, storage) = setupService()
        implicit val context = Context(true, lang)

        val futureTaxonomy = categoryService.getTaxonomyForCategory("invalid",
          Seq.empty[String], 2, 3, Seq.empty[String], storage)(context) map {
          taxonomy => {
            taxonomy must beEqualTo(None)
          }
        }

        Await.result(futureTaxonomy, Duration.Inf)
        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])
      }
    }


    "should return the taxonomy of a product" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled"
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {
        val (categoryService, storage) = setupService()
        implicit val context = Context(true, lang)

        val futureTaxonomy = categoryService.getProductTaxonomy("pid", "outdoorCatalog", Seq.empty[String], Set("outdoorCat41110026")) map { categories =>
          categories.size must beEqualTo(1)
          categories.foreach( category => {
            validateTree(category)
          })
        }

        Await.result(futureTaxonomy, Duration.Inf)
        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])
      }
    }

    "should return the taxonomy of a brand" in {
      running(FakeApplication(
        additionalConfiguration = Map(
          "ehcacheplugin" -> "enabled",
          "solr.error.retry" -> 2
        ),
        withoutPlugins = Seq("com.typesafe.plugin.RedisPlugin")
      )) {
        val (categoryService, storage) = setupService()
        val ancestorFacet = new FacetField("ancestorCategoryId")
        ancestorFacet.add("catRoot", 15)
        ancestorFacet.add("outdoorCat4000003", 15)
        ancestorFacet.add("outdoorCat4100004", 15)
        ancestorFacet.add("outdoorCat41100024", 15)
        ancestorFacet.add("outdoorCat41110026", 10)
        ancestorFacet.add("outdoorCat41110025", 5)
        ancestorFacet.add("outdoorCat11000219", 15)
        ancestorFacet.add("outdoorCat100003", 15)
        queryResponse.getFacetFields returns List(ancestorFacet)
        implicit val context = Context(true, lang)

        val futureTaxonomy = categoryService.getBrandTaxonomy("brandId", "outdoorCatalog", Seq.empty[String]) map { categories => {
          categories.size must beEqualTo(1)
          categories.foreach( category => {
            val expectedChildren = categoryChildMap + ("catRoot" -> Seq("outdoorCat4000003", "outdoorCat100003"))
            validateTree(category, expectedChildren)
          })
        }}

        Await.result(futureTaxonomy, Duration.Inf)
        cleanCache
        there was one(storage).findAllCategories(any[Seq[String]])

      }
    }

  }

  def validateTaxonomy(categoryService: CategoryService, storage: MongoStorage) = {
    categoryService.getTaxonomy(storage, false) map { taxonomy =>
      categoryMap.keys.foreach(key => {
        taxonomy(key) must beEqualTo(categoryMap(key))
      })
      //plus two to include the 2 root catalog nodes this methods adds (outdoorCatalog & otherCatalog)
      taxonomy must have size (categoryMap.size + 2)
    }
  }

  def cleanCache: Unit = {
    for (p <- current.plugin[EhCachePlugin]) {
      p.manager.clearAll
    }
  }

  def validateTree(category: Category, taxonomy: Map[String, Seq[String]]): Unit = {
    category.childCategories match {
      case Some(childCats) => {
        childCats.foreach(childCat => {
          taxonomy(category.getId) must contain(childCat.getId)
          Logger.debug(taxonomy(category.getId) + " contains " + childCat.getId)
          validateTree(childCat)
        })
      }
      case _ => {
        Logger.debug("leaf category: " + category.getId)
      }
    }
  }

  def validateTree(category: Category): Unit = {
    validateTree(category, categoryChildMap)
  }

  @tailrec final def awaitSuccess[A](fs: Seq[Future[A]], done: Seq[A] = Seq()):
  Either[Throwable, Seq[A]] = {
    val first = Future.firstCompletedOf(fs)
    Await.ready(first, Duration.Inf).value match {
      case None => awaitSuccess(fs, done)  // Shouldn't happen!
      case Some(Failure(e)) => Left(e)
      case Some(Success(_)) =>
        val (complete, running) = fs.partition(_.isCompleted)
        val answers = complete.flatMap(_.value)
        answers.find(_.isFailure) match {
          case Some(Failure(e)) => Left(e)
          case _ =>
            if (running.length > 0) awaitSuccess(running, answers.map(_.get) ++: done)
            else Right( answers.map(_.get) ++: done )
        }
    }
  }
}
