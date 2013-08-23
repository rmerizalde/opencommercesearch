package org.opencommercesearch.api.controllers

import play.api.mvc.{AnyContent, Controller, Request}
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
}
