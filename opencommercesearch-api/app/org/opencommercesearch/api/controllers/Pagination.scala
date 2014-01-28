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

import org.opencommercesearch.api.Global.MaxPaginationLimit
import org.opencommercesearch.api.Global.DefaultPaginationLimit

trait Pagination {
  self: Controller =>

  def withPagination(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    val offset = request.getQueryString("offset")
    val limit = request.getQueryString("limit")

    if (offset.isDefined) {
      try {
        query.setStart(offset.get.toInt)
      } catch {
        case e: Throwable => // do nothing
      }
    } else {
      query.setStart(0)
    }

    if (limit.isDefined) {
      try {
        val l = limit.get.toInt
        query.setRows(Math.min(MaxPaginationLimit, l))
      } catch {
        case e: Throwable => // do nothing
      }
    } else {
      query.setRows(DefaultPaginationLimit)
    }

    query
  }

  /**
   * Adds base facet pagination based on offset and limit request params to a given Solr query.
   * @param query The Solr query to update
   * @param request Original request used to extract parameters
   * @return Solr query with additional facet pagination parameters set (if any)
   */
  def withFacetPagination(query: SolrQuery)(implicit request: Request[AnyContent]) : SolrQuery = {
    val offset = request.getQueryString("offset")
    val limit = request.getQueryString("limit")

    if (offset.isDefined) {
      try {
        query.add("facet.offset", offset.get)
      } catch {
        case e: Throwable => // do nothing
      }
    } else {
      query.add("facet.offset", "0")
    }

    if (limit.isDefined) {
      try {
        val l = limit.get.toInt
        query.setFacetLimit(Math.min(MaxPaginationLimit, l))
      } catch {
        case e: Throwable => // do nothing
      }
    } else {
      query.setFacetLimit(DefaultPaginationLimit)
    }

    query
  }
}
