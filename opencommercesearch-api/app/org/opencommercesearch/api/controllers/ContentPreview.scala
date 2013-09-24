package org.opencommercesearch.api.controllers

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

import play.api.mvc.{AnyContent, Request, Controller}

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest

import org.opencommercesearch.api.Global._
import play.api.libs.json.JsValue
import play.api.i18n.Lang

trait ContentPreview {
  self: Controller =>

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
    query.setParam("collection", getProductCollection(preview))
  }

  def withProductCollection[T <: AbstractUpdateRequest](request: T, preview: Boolean)(implicit req: Request[AnyContent]) : T = {
    request.setParam("collection", getProductCollection(preview))
    request
  }

  private def getProductCollection(preview: Boolean)(implicit req: Request[AnyContent]) : String = {
    var collection = ProductPublicCollection
    if (preview) {
      collection = ProductPreviewCollection
    }

    var language: String = "en"

    req.acceptLanguages.map(lang => language = lang.language)
    collection + "_" + language
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

    var language: String = "en"
    acceptLanguages.map(lang => language = lang.language)

    collection = collection + "_" + language

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

    var language: String = "en"
    acceptLanguages.map(lang => language = lang.language)

    collection = collection + "_" + language

    collection
  }
}
