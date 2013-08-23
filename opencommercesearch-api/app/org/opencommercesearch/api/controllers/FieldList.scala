package org.opencommercesearch.api.controllers

import play.api.mvc.{AnyContent, Controller, Request}
import org.apache.solr.client.solrj.SolrQuery

trait FieldList {
  this: Controller =>

  def withFields(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    val fields = request.getQueryString("fields")

    if (fields.isDefined) {
      query.setFields(fields.get.split(','): _*)
    }
    query
  }
}
