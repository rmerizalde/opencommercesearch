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
import play.api.mvc.{Result, Controller, Action}
import play.api.libs.json.{JsError, Json}
import play.api.Logger

import scala.concurrent.Future
import scala.collection.convert.Wrappers.JIterableWrapper

import org.opencommercesearch.api.models.CategoryList
import org.opencommercesearch.api.common.{FieldList, ContentPreview}
import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.opencommercesearch.api.service.CategoryService

object CategoryController extends Controller with ContentPreview with FieldList with Pagination with ErrorHandling {

  val categoryService = new CategoryService(solrServer)

  def findById(version: Int, id: String, preview: Boolean) = Action { implicit request =>
    val future = categoryService.findById(id, preview, request.getQueryString("fields")).map(category => {
      if (category.isDefined) {
        Ok(Json.obj(
          "category" -> Json.toJson(category.get)))
      } else {
        Logger.debug("Category " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find category with id [$id]"
        ))
      }
    })



    Async {
      withErrorHandling(future, s"Cannot retrieve category with id [$id]")
    }
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action(parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[CategoryList](request.body).map { categoryList =>
      val categories = categoryList.categories
      if (categories.size > MaxUpdateCategoryBatchSize) {
        BadRequest(Json.obj(
          "message" -> s"Exceeded number of categories. Maximum is $MaxUpdateCategoryBatchSize"))
      } else {
        try {
          val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
          val docs = categoryList.toDocuments()
          update.add(docs)

          val future: Future[Result] = update.process(solrServer).map( response => {
            Created
          })

          Async {
            withErrorHandling(future, s"Cannot store products with ids [${categories map (_.id) mkString ","}]")
          }
        } catch {
          case e: IllegalArgumentException => {
            Logger.error(e.getMessage)
            BadRequest(Json.obj(
              "message" -> e.getMessage
            ))
          }
        }
      }
    }.recoverTotal {
      case e: JsError => {
        BadRequest(Json.obj(
          "message" -> "Missing required fields"))
      }
    }
  }

  def deleteByTimestamp(version: Int = 1, feedTimestamp: Long, preview: Boolean) = Action { implicit request =>
    val update = withCategoryCollection(new AsyncUpdateRequest(), preview)
    update.deleteByQuery("-feedTimestamp:" + feedTimestamp)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    Async {
      withErrorHandling(future, s"Cannot delete product before feed timestamp [$feedTimestamp]")
    }
  }

  def findSuggestions(version: Int, query: String, preview: Boolean) = Action { implicit request =>
    val solrQuery = withPagination(withCategoryCollection(withFields(new SolrQuery(query), request.getQueryString("fields")), preview))

    val future = solrServer.query(solrQuery).map( response => {
      val docs = response.getResults
      Ok(Json.obj(
        "metadata" -> Json.obj("found" -> docs.getNumFound),
        "suggestions" -> JIterableWrapper(docs).map(doc => doc.get("name").asInstanceOf[String])
      ))
    })

    Async {
      withErrorHandling(future, s"Cannot suggest categories for [$query]")
    }
  }
}
