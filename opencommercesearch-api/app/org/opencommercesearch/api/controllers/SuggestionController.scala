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
import play.api.Play
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future
import scala.collection.mutable

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.I18n._
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.suggester.MultiSuggester
import org.opencommercesearch.search.Element
import org.opencommercesearch.search.collector.{SimpleCollector, MultiSourceCollector}

//import com.wordnik.swagger.annotations._

/**
 * The controller for generic suggestions
 *
 * @author rmerizalde
 */
//@Api(value = "suggestions", basePath = "/api-docs/suggestions", description = "Suggestion API endpoints")
object SuggestionController extends BaseController {

  val suggester = new MultiSuggester

  /**
   * @param source is the collector source
   * @return return the capacity for the collector source
   */
  private def collectorCapacity(source: String) : Int = {
    Play.current.configuration.getInt(s"suggester.$source.collector.capacity").getOrElse(SimpleCollector.DefaultCapacity)
  }

  //@ApiOperation(value = "Suggests user queries, products, categories, brands, etc.", notes = "Returns suggestions for given partial user query", response = classOf[UserQuery], httpMethod = "GET")
  //@ApiResponses(value = Array(new ApiResponse(code = 400, message = "Partial product title is too short")))
  def findSuggestions(
     version: Int,
     //@ApiParam(value = "Partial user query", required = true)
     //@QueryParam("q")
     q: String,
     //@ApiParam(value = "Site to search for suggestions", required = true)
     //@QueryParam("site")
     site: String,
     //@ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
     //@QueryParam("preview")
     preview: Boolean) = Action.async { implicit request =>

    if (q == null || q.length < 2) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"At least $MinSuggestQuerySize characters are needed to make suggestions"
      )))
    } else {
      implicit val context = Context(preview, language)
      val startTime = System.currentTimeMillis()
      val collector = new MultiSourceCollector[Element]
      suggester.sources().map(source => collector.add(source, new SimpleCollector[Element](collectorCapacity(source))) )

      var query = q;
      if(!(q.startsWith("\"") && q.endsWith("\""))) {
        query = "\"" + q + "\""
      }
      
      suggester.search(query, site, collector, solrServer).flatMap(c => {
        if (!collector.isEmpty) {
          var futureList = new mutable.ArrayBuffer[Future[(String, Json.JsValueWrapper)]]

          for (source <- collector.sources) {
            for (c <- collector.collector(source)) {
              futureList += Future.successful(suggester.responseName(source) -> c.elements().map(e => e.toJson))
            }
          }

          Future.sequence(futureList).map( results => {
            Ok(Json.obj(
              "metadata" -> Json.obj(
                 "found" -> collector.size(),
                 "time" -> (System.currentTimeMillis() - startTime)),
               "suggestions" -> Json.obj(
                 results:_*
               )
             ))
          })
        } else {
          Future.successful(Ok(Json.obj(
           "metadata" -> Json.obj(
              "found" -> 0,
              "time" -> (System.currentTimeMillis() - startTime))
          )))
        }
      })
    }
  }
}


