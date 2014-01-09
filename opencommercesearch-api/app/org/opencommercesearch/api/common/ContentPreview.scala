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
import org.jongo.MongoCollection
import org.opencommercesearch.api.service.{StorageFactory, Storage}

trait ContentPreview {

  val SupportedCountries = Seq("US", "CA")
  val SupportedLanguages = Seq("en", "fr")

  def withQueryCollection(query: SolrQuery, preview: Boolean) : SolrQuery = {
    query.setParam("collection", QueryCollection)
  }

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

  def withNamespace[R, T](factory: StorageFactory[T], preview: Boolean)(implicit req: Request[R]) : Storage[T] = {
    var namespace = "public"
    if (preview) {
      namespace = "preview"
    }
    namespace += "_" + language(req.acceptLanguages)
    factory.getInstance(namespace)
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
    collection + "_" + language(acceptLanguages)
  }

  def withSearchCollection(query: SolrQuery, preview: Boolean)(implicit req: Request[AnyContent]) : SolrQuery = {
    query.setParam("collection", getSearchCollection(preview, req.acceptLanguages))
  }

  def withSearchCollection[T <: AbstractUpdateRequest, R](request: T, preview: Boolean)(implicit req: Request[R]) : T = {
    request.setParam("collection", getSearchCollection(preview, req.acceptLanguages))
    request
  }

  private def getSearchCollection(preview: Boolean, acceptLanguages:Seq[Lang]) : String = {
    var collection = SearchPublicCollection
    if (preview) {
      collection = SearchPreviewCollection
    }

    collection + "_" + language(acceptLanguages)
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

  def withRuleCollection(query: SolrQuery, preview: Boolean, acceptLanguages:Seq[Lang]) : SolrQuery = {
    query.setParam("collection", getRuleCollection(preview, acceptLanguages))
  }

  def withRuleCollection[T <: AbstractUpdateRequest](request: T, preview: Boolean, acceptLanguages:Seq[Lang]) : T = {
    request.setParam("collection", getRuleCollection(preview, acceptLanguages))
    request
  }

  private def getRuleCollection(preview: Boolean, acceptLanguages:Seq[Lang]) : String = {
    var collection = RulePublicCollection
    if (preview) {
      collection = RulePreviewCollection
    }

    collection = collection + "_" + language(acceptLanguages)
    collection
  }

  def withFacetCollection(query: SolrQuery, preview: Boolean, acceptLanguages:Seq[Lang]) : SolrQuery = {
    query.setParam("collection", getFacetCollection(preview, acceptLanguages))
  }

  def withFacetCollection[T <: AbstractUpdateRequest](request: T, preview: Boolean, acceptLanguages:Seq[Lang]) : T = {
    request.setParam("collection", getFacetCollection(preview, acceptLanguages))
    request
  }

  private def getFacetCollection(preview: Boolean, acceptLanguages:Seq[Lang]) : String = {
    var collection = FacetPublicCollection
    if (preview) {
      collection = FacetPreviewCollection
    }

    collection = collection + "_" + language(acceptLanguages)
    collection
  }

  def country(acceptLanguages:Seq[Lang]) : String = {
    var country: String = "US"

    acceptLanguages.collectFirst { case lang if SupportedCountries.contains(lang.country) => country = lang.country }
    country
  }

  def language(acceptLanguages:Seq[Lang]) : String = {
    var language: String = "en"

    acceptLanguages.collectFirst { case lang if SupportedLanguages.contains(lang.language) => language = lang.language }
    language
  }

}
