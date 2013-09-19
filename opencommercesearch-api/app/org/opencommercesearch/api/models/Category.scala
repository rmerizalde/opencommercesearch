package org.opencommercesearch.api.models

import play.api.libs.json._

import java.util

import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.beans.Field
import scala.collection.convert.Wrappers.JIterableWrapper

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

/**
 * A category model
 *
 * @param id is the category id
 * @param name is the category name
 * @param isRuleBased indicates if this a rule based category or not
 * @param catalogs the catalogs the categories belongs to
 * @param parentCategories the parent categories. Empty for root categories
 */
case class Category(
  var id: Option[String],
  var name: Option[String],
  var isRuleBased: Option[Boolean],
  var catalogs: Option[Seq[String]],
  var parentCategories: Option[Seq[String]]) {

  def this() = this(None, None, None, None, None)

  @Field
  def setId(id: String) {
    this.id = Some(id)
  }

  @Field
  def setName(name: String) {
    this.name = Some(name)
  }

  @Field("isRuleBased")
  def setRuleBased(isRuleBased: Boolean) {
    this.isRuleBased = Some(isRuleBased)
  }

  @Field
  def setCatalogs(catalogs: util.Collection[String]) {
    this.catalogs = Some(JIterableWrapper(catalogs).toSeq)
  }

  @Field
  def setParentCategories(catalogs: util.Collection[String]) {
    this.parentCategories = Some(JIterableWrapper(catalogs).toSeq)
  }

}

object Category {
  implicit val readsCategory = Json.reads[Category]
  implicit val writesCategory = Json.writes[Category]
}

case class CategoryList(categories: Seq[Category], feedTimestamp: Long) {
  def toDocuments() : util.List[SolrInputDocument] = {
    val documents = new util.ArrayList[SolrInputDocument](categories.size)
    var expectedDocCount = 0
    var currentDocCount = 0

    for (category: Category <- categories) {
      expectedDocCount += 1
      for (id <- category.id; name <- category.name; isRuleBased <- category.isRuleBased) {
        val doc = new SolrInputDocument()
        doc.setField("id", id)
        doc.setField("name", name)
        doc.setField("isRuleBased", isRuleBased)
        doc.setField("feedTimestamp", feedTimestamp)
        for (catalogs <- category.catalogs) {
          for (catalog <- catalogs) {
            doc.addField("catalogs", catalog)
          }
        }
        for (parentCategories <- category.parentCategories) {
          for (parentCategory <- parentCategories) {
            doc.addField("parentCategories", parentCategory)
          }
        }
        documents.add(doc)
        currentDocCount += 1
      }
      if (expectedDocCount != currentDocCount) {
        throw new IllegalArgumentException("Missing required fields for category " + category.id.get)
      }
    }


    return documents
  }
}

object CategoryList {
  implicit val readsCategoryList = Json.reads[CategoryList]
  implicit val writesCategoryList = Json.writes[CategoryList]
}
