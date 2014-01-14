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
import com.wordnik.swagger.annotations._
import javax.ws.rs.QueryParam

/**
 * The controller for query suggestions
 *
 * @author rmerizalde
 */
@Api(value = "queries", basePath = "/api-docs/queries", description = "User Query API endpoints")
object QueryController extends BaseController {

  @ApiOperation(value = "Suggests user queries", notes = "Returns brand suggestions for given partial user query", response = classOf[UserQuery], httpMethod = "GET")
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the complete suggestion result set", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of suggestions", defaultValue = "10", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "userQuery", required = false, dataType = "string", paramType = "query")
  ))
  def findSuggestions(
      version: Int,
      @ApiParam(value = "Partial user query", required = true)
      @QueryParam("q")
      q: String,
      @ApiParam(value = "Site to search for user query suggestions", required = true)
      @QueryParam("site")
      site: String,
      @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
      @QueryParam("preview")
      preview: Boolean) = Action.async { implicit request =>
    val solrQuery = withQueryCollection(new SolrQuery(q), preview)
    solrQuery.setFields("userQuery")
    solrQuery.setFilterQueries(s"siteId:$site")

    findSuggestionsFor(classOf[UserQuery], "queries" , solrQuery)
  }
}
