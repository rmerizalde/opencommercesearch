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
import play.api.i18n.Lang.preferred
import play.api.Play.current

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.service.{StorageFactory, Storage}
import org.opencommercesearch.common.Context

trait ContentPreview {

  val SupportedCountries = Seq("US", "CA")
  val SupportedLanguages = Seq("en", "fr")

  def withSuggestCollection(query: SolrQuery, preview: Boolean) : SolrQuery = {
    query.setParam("collection", SuggestCollection)
  }

  def withSuggestCollection[T <: AbstractUpdateRequest](request: T) : T = {
    request.setParam("collection", SuggestCollection)
    request
  }

  def withNamespace[T](factory: StorageFactory[T])(implicit context: Context) : Storage[T] = {
    var namespace = "public"
    if (context.isPreview) {
      namespace = "preview"
    }
    namespace += "_" + context.lang.language
    factory.getInstance(namespace)
  }

  def withNamespace[R, T](factory: StorageFactory[T], preview: Boolean)(implicit req: Request[R]) : Storage[T] = {
    var namespace = "public"
    if (preview) {
      namespace = "preview"
    }
    namespace += "_" + language(req.acceptLanguages)
    factory.getInstance(namespace)
  }

  def withCategoryCollection(query: SolrQuery)(implicit context: Context) : SolrQuery = {
    query.setParam("collection", getCategoryCollection(context.isPreview))
  }

  def withCategoryCollection[T <: AbstractUpdateRequest, R](request: T)(implicit context: Context) : T = {
    request.setParam("collection", getCategoryCollection(context.isPreview))
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

  private def country(acceptLanguages:Seq[Lang]) : String = {
    preferred(acceptLanguages).country
  }

  private def language(acceptLanguages:Seq[Lang]) : String = {
    preferred(acceptLanguages).language
  }

}
