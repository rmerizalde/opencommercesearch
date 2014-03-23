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

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.common.ContentPreview
import org.apache.solr.client.solrj.SolrQuery
import scala.concurrent.Future
import play.api.mvc.SimpleResult

/**
 * A simple check to verify Solr and Mongo are healthy
 */
object HealthCheckController extends Controller with ContentPreview {


  def checkGet(version: Int, preview: Boolean) = Action.async  { implicit request =>
    checkHealth(version, preview)
  }

  def checkHead(version: Int, preview: Boolean) = Action.async  { implicit request =>
    checkHealth(version, preview)
  }

  def checkHealth(version: Int, preview: Boolean)(implicit request: Request[AnyContent]) : Future[SimpleResult] = {
    val startTime = System.currentTimeMillis()
    val query = withProductCollection(new SolrQuery("*:*"), preview)

    query.setRows(0)
    solrServer.query(query).flatMap(response => {
      if (response.getResults.getNumFound > 0) {
        val storage = withNamespace(storageFactory, preview)

        storage.countProducts().map(count => {
          if (count > 0) {
            Ok
          } else {
            InternalServerError
          }
        }).recover {
          case _ => InternalServerError
        }
      } else {
        Future.successful(InternalServerError)
      }
    }).map(result => {
      result.withHeaders(("X-QTime", (System.currentTimeMillis() - startTime).toString))
    })
  }

}
