package org.opencommercesearch.api.controllers

import play.api.mvc.{AnyContent, Request, Controller}
import org.apache.solr.client.solrj.SolrQuery

import org.opencommercesearch.api.Global.MaxPaginationLimit

trait Pagination {
  self: Controller =>

  def withPagination(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    val offset = request.getQueryString("offset")
    val limit = request.getQueryString("limit")

    if (offset.isDefined) {
      try {
        query.setStart(offset.get.toInt)
      } catch {
        case e => // do nothing
      }
    }

    if (limit.isDefined) {
      try {
        val l = limit.get.toInt
        query.setRows(Math.min(MaxPaginationLimit, l))
      } catch {
        case e => // do nothing
      }
    }

    query
  }
}
