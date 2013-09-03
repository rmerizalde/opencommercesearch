package org.opencommercesearch.api.controllers

import play.api.mvc.{AnyContent, Request, Controller}

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest

import org.opencommercesearch.api.Global._


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


}
