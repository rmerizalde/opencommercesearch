package org.opencommercesearch.api.common

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

import play.api.mvc.{AnyContent, Request}
import play.api.i18n.Lang

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest

import org.opencommercesearch.api.Global._

trait ContentPreview {

  val SupportedLanguages = Seq("en", "fr")

  def withBrandCollection(query: SolrQuery, preview: Boolean) : SolrQuery = {
    query.setParam("collection", getBrandCollection(preview))
  }

  def withBrandCollection[T <: AbstractUpdateRequest](request: T, preview: Boolean) : T = {
    request.setParam("collection", getBrandCollection(preview))
    request
  }

  private def getBrandCollection(preview: Boolean) : String = {
    var collection = BrandPublicCollection
    if (preview) {
      collection = BrandPreviewCollection
    }
    collection
  }

  def withProductCollection(query: SolrQuery, preview: Boolean)(implicit req: Request[AnyContent]) : SolrQuery = {
    query.setParam("collection", getProductCollection(preview, req.acceptLanguages))
  }

  def withProductCollection[T <: AbstractUpdateRequest, R](request: T, preview: Boolean)(implicit req: Request[R]) : T = {
    request.setParam("collection", getProductCollection(preview, req.acceptLanguages))
    request
  }

  private def getProductCollection(preview: Boolean, acceptLanguages:Seq[Lang]) : String = {
    var collection = ProductPublicCollection
    if (preview) {
      collection = ProductPreviewCollection
    }

    var language: String = "en"

    acceptLanguages.map(lang => language = lang.language)

    if (!SupportedLanguages.contains(language)) {
      language = "en"
    }
    collection + "_" + language
  }

  def withCategoryCollection(query: SolrQuery, preview: Boolean) : SolrQuery = {
    query.setParam("collection", getCategoryCollection(preview))
  }

  def withCategoryCollection[T <: AbstractUpdateRequest, R](request: T, preview: Boolean) : T = {
    request.setParam("collection", getCategoryCollection(preview))
    request
  }

  private def getCategoryCollection(preview: Boolean) : String = {
    var collection = CategoryPublicCollection
    if (preview) {
      collection = CategoryPreviewCollection
    }
    collection
  }


}
