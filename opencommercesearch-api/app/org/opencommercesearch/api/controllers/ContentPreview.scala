package org.opencommercesearch.api.controllers

import play.api.mvc.{AnyContent, Request, Controller}
import play.api.i18n.Lang

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest

import org.opencommercesearch.api.Global._
import play.api.libs.json.JsValue


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
    collection + "_" + language
  }


}
