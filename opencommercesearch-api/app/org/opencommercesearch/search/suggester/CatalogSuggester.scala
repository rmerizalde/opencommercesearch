package org.opencommercesearch.search.suggester

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
import scala.collection.mutable
import scala.collection.JavaConversions._

import org.apache.solr.common.SolrDocument
import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}
import org.apache.solr.client.solrj.beans.DocumentObjectBinder
import org.opencommercesearch.api.Global.{storageFactory, SuggestCollection}
import org.opencommercesearch.search.Element
import org.opencommercesearch.api.models.{UserQuery, Category, Product, Brand}
import org.opencommercesearch.api.common.ContentPreview
import org.opencommercesearch.common.Context
import org.opencommercesearch.api.ProductSearchQuery
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.lang.StringUtils


/**
 * Suggests elements from the catalog. The elements include brands, categories and products. Additionally, the suggester
 * returns popular user queries. Suggestions come from the autocomplete collection and are based on ngrams on the brand name,
 * category name and product title. User queries used edge ngrams.
 *
 * @author rmerizalde
 */
class CatalogSuggester[E <: Element] extends Suggester[E] with ContentPreview {

  private trait ElementBinder {
    def getElement(doc: SolrDocument)(implicit context : Context) : Future[E]
    def getElements(doc: Seq[SolrDocument])(implicit context : Context) : Future[Seq[E]]

    protected def ids(docs: Seq[SolrDocument]) = docs.map(doc => doc.getFieldValue("id").asInstanceOf[String])
  }

  private class UserQueryBinder extends ElementBinder {
    val binder = new DocumentObjectBinder()

    def getElement(doc: SolrDocument)(implicit context : Context) : Future[E] = {
      Future.successful(binder.getBean(classOf[UserQuery], doc).asInstanceOf[E])
    }

    def getElements(doc: Seq[SolrDocument])(implicit context : Context) : Future[Seq[E]] = {
      Future.successful(doc.map(d => binder.getBean(classOf[UserQuery], d).asInstanceOf[E]))
    }
  }

  private class BrandBinder extends ElementBinder {
    val fields = Seq("name", "logo", "url")

    def getElement(doc: SolrDocument)(implicit context : Context) : Future[E] = {
      withNamespace(storageFactory).findBrand(doc.get("id").asInstanceOf[String], fields).map(b => {
        b.asInstanceOf[E]
      })
    }

    def getElements(docs: Seq[SolrDocument])(implicit context : Context) : Future[Seq[E]] = {
      withNamespace(storageFactory).findBrands(ids(docs), fields).map(brands => {
        brands.toSeq.asInstanceOf[Seq[E]]
      })
    }
  }

  private class CategoryBinder extends ElementBinder {
    val fields = Seq("name", "seoUrlToken")

    def getElement(doc: SolrDocument)(implicit context : Context) : Future[E] = {
      withNamespace(storageFactory).findCategory(doc.get("id").asInstanceOf[String], fields).map(b => {
        b.asInstanceOf[E]
      })
    }

    def getElements(docs: Seq[SolrDocument])(implicit context : Context) : Future[Seq[E]] = {
      withNamespace(storageFactory).findCategories(ids(docs), fields).map(categories => {
        categories.toSeq.asInstanceOf[Seq[E]]
      })
    }
  }

  private class ProductBinder extends ElementBinder {

    def getElement(doc: SolrDocument)(implicit context : Context) : Future[E] = {
      withNamespace(storageFactory).findProduct(doc.get("id").asInstanceOf[String], context.lang.country, ProductSuggester.fields).map(b => {
        b.asInstanceOf[E]
      })
    }

    def getElements(docs: Seq[SolrDocument])(implicit context : Context) : Future[Seq[E]] = {
      val ids: Seq[(String, String)] = docs.map(doc => (doc.getFieldValue("id").asInstanceOf[String], null))
      withNamespace(storageFactory).findProducts(ids, context.lang.country, ProductSuggester.fields, minimumFields = false).map(products => {
        products.toSeq.asInstanceOf[Seq[E]]
      })
    }
  }

  private object ProductSuggester extends ContentPreview {

    val fields = Seq("id", "title", "url", "brand.name", "skus.image", "skus.url")
    val empty = Seq[E]()

    def search(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[E]] = {
      val query = new ProductSearchQuery(q, site)(context, null)
        .withPagination(offset = 0, suggestionLimit)
        .withGrouping(totalCount = false, limit = 1, collapse = false)

      server.query(query).flatMap( response => {
        val ids = new ArrayBuffer[(String, String)](suggestionLimit)

        if (response.getGroupResponse != null) {
          for (command <- response.getGroupResponse.getValues) {
            for (group <- command.getValues) {
              val productId = group.getGroupValue
              val doc = group.getResult.get(0)

              ids.append((productId, doc.getFieldValue("id").asInstanceOf[String]))
            }
          }
        }

        if (ids.size > 0) {
          withNamespace(storageFactory).findProducts(ids, context.lang.country, fields, minimumFields = true).map(products => {
            products.foreach(p => {
              for (skus <- p.skus) {
                skus.foreach(sku => {
                  // workaround, fields selection don't work with sku filtering currently in mongo
                  sku.id = None
                  sku.isPastSeason = None
                  sku.title = None
                  sku.isRetail = None
                  sku.isCloseout = None
                  sku.isOutlet = None
                })
              }
            })
            products.toSeq.asInstanceOf[Seq[E]]
          })
        } else {
          Future.successful(empty)
        }
      })
    }
  }

  private val suggestionLimit = 10

  private val typeToClass = Map(
      "brand" -> classOf[Brand],
      "product" -> classOf[Product],
      "category" -> classOf[Category],
      "userQuery" -> classOf[UserQuery]
  )

  private val typeToBinder = Map[String, ElementBinder](
    "userQuery" -> new UserQueryBinder(),
    "brand" -> new BrandBinder(),
    "category" -> new CategoryBinder(),
    "product" -> new ProductBinder()
  )

  private val source2ResponseName = Map(
    "product" -> "products",
    "userQuery" -> "queries",
    "category" -> "categories",
    "brand" -> "brands"
  )

  override def responseName(source: String) : String = source2ResponseName.get(source).getOrElse(source)

  override def sources() = typeToClass.keySet

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer)(implicit context : Context) : Future[Seq[E]] = {
    val query = new SolrQuery(q)
    query.setParam("collection", SuggestCollection)
      .setFields("id", "userQuery")
      .setFilterQueries(s"siteId:$site")
      .set("group", true)
      .set("group.ngroups", false)
      .set("group.field", "type")
      .set("group.facet", false)
      .set("group.limit", suggestionLimit)
      .set("defType", "edismax")
      .set("qf", "userQuery ngrams")

    server.query(query).flatMap( response => {
      val futureList = new mutable.ArrayBuffer[Future[Seq[E]]](typeToBinder.size)
      var suggestedTerm = StringUtils.EMPTY 
      var suggestedProducts: Future[Seq[E]] = null

      if (response.getGroupResponse != null) {
        for (command <- response.getGroupResponse.getValues) {

          for (group <- command.getValues) {
            val `type` = if (group.getGroupValue == null) "userQuery" else group.getGroupValue
            val clazz = typeToClass.get(`type`).getOrElse(null)

            if (clazz != null) {
              val docs = group.getResult.map(doc => amendId(doc, `type`))

              if(`type` == "userQuery" && StringUtils.isEmpty(suggestedTerm) && docs.length > 0) {
                suggestedTerm = docs.get(0).getFieldValue("userQuery").toString
              }

              for (binder <- typeToBinder.get(`type`)) {
                if(`type` == "product") {
                  suggestedProducts = binder.getElements(docs)
                } else {
                  futureList += binder.getElements(docs)
                }
              }
            }
          }
        }
      }

      if (StringUtils.isNotEmpty(suggestedTerm)) {
        futureList += ProductSuggester.search(suggestedTerm, site, server)
      }

      if (suggestedProducts != null) {
        futureList += suggestedProducts
      }

      Future.sequence(futureList).map( elements => {
        elements.flatten
      })
    })
  }

  private def amendId(doc: SolrDocument, `type`: String) : SolrDocument = {
    val id = doc.getFieldValue("id")
    if (id != null && id.isInstanceOf[String]) {
      val strId = id.asInstanceOf[String]
      if (strId.startsWith(`type`) && strId.size > (`type`.size + 1)) {
        doc.setField("id", strId.substring(`type`.size + 1))
      }
    }
    doc
  }
}
