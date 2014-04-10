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

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.libs.json.{JsValue, JsNull, Json, JsObject}

import scala.concurrent.Future

import javax.ws.rs.QueryParam

import org.opencommercesearch.api.Global._
import org.opencommercesearch.search.suggester.GroupingSuggester
import org.opencommercesearch.search.Element
import org.opencommercesearch.api.models.{Product, Brand, Category, UserQuery}

import com.wordnik.swagger.annotations._
import org.opencommercesearch.search.collector.{SimpleCollector, MultiSourceCollector}



/**
 * The controller for generic suggestions
 *
 * @author rmerizalde
 */
@Api(value = "suggestions", basePath = "/api-docs/suggestions", description = "Suggestion API endpoints")
object SuggestionController extends BaseController {

  val groupingSuggester = new GroupingSuggester[Element](Map(
    ("brand" -> classOf[Brand]),
    ("product" -> classOf[Product]),
    ("category" -> classOf[Category]),
    ("userQuery" -> classOf[UserQuery])
  ))

  @ApiOperation(value = "Suggests user queries, products, categories, brands, etc.", notes = "Returns suggestions for given partial user query", response = classOf[UserQuery], httpMethod = "GET")
  def findSuggestions(
     version: Int,
     @ApiParam(value = "Partial user query", required = true)
     @QueryParam("q")
     q: String,
     @ApiParam(value = "Site to search for suggestions", required = true)
     @QueryParam("site")
     site: String,
     @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
     @QueryParam("preview")
     preview: Boolean) = Action.async { implicit request =>


    if (q == null || q.length < 2) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"At least $MinSuggestQuerySize characters are needed to make suggestions"
      )))
    } else {
      val startTime = System.currentTimeMillis()
      val collector = new MultiSourceCollector[Element]
      groupingSuggester.typeToClass.keys.map(source => collector.add(source, new SimpleCollector[Element]) )

      // @todo add site
      groupingSuggester.search(q, site, collector, solrServer).map(c => {
        if (!collector.isEmpty) {

          var queries: Seq[JsValue] = null
          var products: Seq[JsValue] = null
          var brands: Seq[JsValue] = null
          var categories: Seq[JsValue] = null

          for (c <- collector.collector("userQuery")) {
            queries = c.elements().map(e => {
              Json.toJson(e.asInstanceOf[UserQuery])
            })
          }
          for (c <- collector.collector("product")) {
            products = c.elements().map(e => Json.toJson(e.asInstanceOf[Product]))
          }
          for (c <- collector.collector("brand")) {
            brands = c.elements().map(e => Json.toJson(e.asInstanceOf[Brand]))
          }
          for (c <- collector.collector("category")) {
            categories = c.elements().map(e => Json.toJson(e.asInstanceOf[Category]))
          }

          Ok(Json.obj(
           "metadata" -> Json.obj(
              "found" -> collector.size(),
              "time" -> (System.currentTimeMillis() - startTime)),
            "suggestions" -> Json.obj(
              "queries" -> queries,
              "products" -> products,
              "brands" -> brands,
              "categories" -> categories
            )
          ))
        } else {
          Ok(Json.obj(
           "metadata" -> Json.obj(
              "found" -> 0,
              "time" -> (System.currentTimeMillis() - startTime))
         ))
        }
      })
    }



  }
}


