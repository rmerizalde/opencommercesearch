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

import play.api.mvc._

import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.models.UserQuery

/**
 * The controller for query suggestions
 *
 * @author rmerizalde
 */
object QueryController extends BaseController {
  def findSuggestions(version: Int, q: String, site: String, preview: Boolean) = Action { implicit request =>
    val solrQuery = withQueryCollection(new SolrQuery(q), preview)
    solrQuery.setFields("userQuery")
    solrQuery.setFilterQueries(s"siteId:$site")

    Async {
      findSuggestionsFor(classOf[UserQuery], "queries" , solrQuery)
    }
  }
}
