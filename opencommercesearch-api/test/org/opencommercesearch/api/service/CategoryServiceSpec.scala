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

import play.api.test.Helpers._
import play.api.test.FakeApplication

import scala.concurrent.Future

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.mockito.Matchers
import org.opencommercesearch.common.Context
import org.opencommercesearch.api.models.{Category, Product}
import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.apache.solr.common.util.NamedList
import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import play.api.i18n.Lang

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

  private def setup() : Unit = {
    // Root
    mockCategory(catRoot, "catRoot", "root", categoryCatalogs, null, isRuleBased = false)
    // Rules Based
    mockCategory(catRulesBased, "catRulesBased", "Rules Based", categoryCatalogs, newSet(catRoot), isRuleBased = true)
    // Shoes & Footwear
    mockCategory(catShoesFootwear, "outdoorCat4000003", "Shoes & Footwear", categoryCatalogs, newSet(catRoot), isRuleBased = false)
    // Men's Shoes & Boots
    mockCategory(catMensShoesBoots, "outdoorCat4100004", "Men's Shoes & Boots", categoryCatalogs, newSet(catShoesFootwear), isRuleBased = false)
    // Men's Clothing
    mockCategory(catMensClothing, "outdoorCat100003", "Men's Clothing", categoryCatalogs, newSet(catRoot), isRuleBased = false)
    // Men's Shoes & Footwear
    mockCategory(catMensShoesFootwear, "outdoorCat11000219", "Men's Shoes & Footwear", categoryCatalogs, newSet(catMensClothing), isRuleBased = false)
    // Men's Rain Boots & Shoes
    mockCategory(catMensRainBootsShoes, "outdoorCat41100024", "Men's Rain Boots & Shoes", categoryCatalogs, newSet(catMensShoesBoots, catMensShoesFootwear), isRuleBased = false)
    // Men's Rain Shoes
    mockCategory(catMensRainShoes, "outdoorCat41110026", "Men's Rain Shoes", categoryCatalogs, newSet(catMensRainBootsShoes), isRuleBased = false)
    // Men's Rain Boots
    mockCategory(catMensRainBoots, "outdoorCat41110025", "Men's Rain Boots", categoryCatalogs, newSet(catMensRainBootsShoes), isRuleBased = false)
    // Snowshoe
    mockCategory(catSnowshoe, "outdoorCat11000003", "Snowshoe", categoryCatalogs, newSet(catRoot), isRuleBased = false)
    // Snowshoe Accessories
    mockCategory(catSnowshoeAccessories, "outdoorCat111000028", "Snowshoe Accessories", categoryCatalogs, newSet(catSnowshoe), isRuleBased = false)
    // Snowshoe Footwear
    mockCategory(catSnowshoeFootwear, "outdoorCat111100030", "Snowshoe Footwear", categoryCatalogs, newSet(catSnowshoeAccessories), isRuleBased = false)
    // Snowshoe boots
    mockCategory(catSnowshoeBoots, "outdoorCat111110031", "Snowshoe Boots", categoryCatalogs, newSet(catSnowshoeFootwear), isRuleBased = false)

    mockCategory(rootOtherCategory, "rootOtherCategory", "Root Other Category", Seq(otherCatalog), null, isRuleBased = false)
    mockCategory(otherCategory, "otherCategory", "Other Category", Seq(otherCatalog), newSet(rootOtherCategory), isRuleBased = false)

  }

  private def setupService() : CategoryService = {
    val server = mock[AsyncSolrServer]
    val binder = mock[DocumentObjectBinder]
    val service = spy(new CategoryService(server))
    val categoryDocMap = Map(
      "catRoot" -> mock[SolrDocument],
      "catRulesBased" -> mock[SolrDocument],
      "outdoorCat4000003" -> mock[SolrDocument],
      "outdoorCat4100004" -> mock[SolrDocument],
      "outdoorCat100003" -> mock[SolrDocument],
      "outdoorCat11000219" -> mock[SolrDocument],
      "outdoorCat41100024" -> mock[SolrDocument],
      "outdoorCat41110026" -> mock[SolrDocument],
      "outdoorCat41110025" -> mock[SolrDocument],
      "outdoorCat11000003" -> mock[SolrDocument],
      "outdoorCat111000028" -> mock[SolrDocument],
      "outdoorCat111100030" -> mock[SolrDocument],
      "outdoorCat111110031" -> mock[SolrDocument],
      "rootOtherCategory" -> mock[SolrDocument],
      "otherCategory" -> mock[SolrDocument])
    val categoryMap = Map(
      categoryDocMap.get("catRoot").get -> catRoot,
      categoryDocMap.get("catRulesBased").get -> catRulesBased,
      categoryDocMap.get("outdoorCat4000003").get -> catShoesFootwear,
      categoryDocMap.get("outdoorCat4100004").get -> catMensShoesBoots,
      categoryDocMap.get("outdoorCat100003").get -> catMensClothing,
      categoryDocMap.get("outdoorCat11000219").get -> catMensShoesFootwear,
      categoryDocMap.get("outdoorCat41100024").get -> catMensRainBootsShoes,
      categoryDocMap.get("outdoorCat41110026").get -> catMensRainShoes,
      categoryDocMap.get("outdoorCat41110025").get -> catMensRainBoots,
      categoryDocMap.get("outdoorCat11000003").get -> catSnowshoe,
      categoryDocMap.get("outdoorCat111000028").get -> catSnowshoeAccessories,
      categoryDocMap.get("outdoorCat111100030").get -> catSnowshoeFootwear,
      categoryDocMap.get("outdoorCat111110031").get -> catSnowshoeBoots,
      categoryDocMap.get("rootOtherCategory").get -> rootOtherCategory,
      categoryDocMap.get("otherCategory").get -> otherCategory)

    server.query(any[SolrQuery]) answers { q =>
      val query = q.asInstanceOf[SolrQuery]
      val queryResponse = mock[QueryResponse]
      val response = mock[NamedList[Object]]

      queryResponse.getResponse returns response
      queryResponse.getResponse.get("doc") returns categoryDocMap.get(query.get("id")).get
      Future.successful(queryResponse)
    }

    server.binder returns binder

    binder.getBean(Matchers.eq(classOf[Category]), any[SolrDocument]) answers { (args, mock) =>
      val arguments = args.asInstanceOf[Array[AnyRef]]
      val doc = arguments(1).asInstanceOf[SolrDocument]
      categoryMap.get(doc).get
    }

    service
  }

  private def mockCategory(category: Category, categoryId: String, displayName: String, categoryCatalogs: Seq[String],
    parentCategories: Seq[Category] , isRuleBased: Boolean) : Unit = {
    category.getId returns categoryId
    category.id returns Some(categoryId)
    category.name returns Some(displayName)
    category.sites returns Some(categoryCatalogs)
    if (parentCategories != null) {
      category.parentCategories returns Some(parentCategories)
    } else {
      category.parentCategories returns None
    }
    category.isRuleBased returns Some(isRuleBased)
  }

  private def newSet(items: Category*) : Seq[Category] = {
     Seq(items:_*)
  }

  "CategoryService" should {
    setup()

    "include rule based category for ancestor" in {
      running(FakeApplication()) {
        val product = mock[Product]
        val doc = mock[SolrInputDocument]
        val categoryService = setupService()
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
        val categoryService = setupService()
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
        val categoryService = setupService()
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
  }
}
