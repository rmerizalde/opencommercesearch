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
import play.api.Logger
import play.api.libs.json.{JsError, JsArray, Json}
import scala.concurrent.Future
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.util.Util
import org.opencommercesearch.api.models.{Category, Rule, RuleList}
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.beans.BindingException
import org.apache.solr.common.SolrDocument
import Util._
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.api.service.CategoryService.Taxonomy
import scala.util.Success

object RuleController extends BaseController {

  var categoryService = new CategoryService(solrServer, storageFactory)
  
  def findById(version: Int, id: String, preview: Boolean) = Action.async { implicit request =>
    val query = withRuleCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview, request.acceptLanguages)

    query.add("q", "id:" + id)
    query.add("fl", "*")

    Logger.debug("Query rule " + id)
    val future = solrServer.query(query).map( response => {
      val results = response.getResults
      if(results.getNumFound > 0 && results.get(0) != null) {
        var doc : SolrDocument = results.get(0)
        Logger.debug("Found rule " + id)
        Ok(Json.obj(
          "rule" -> solrServer.binder.getBean(classOf[Rule], doc)))
      } else {
        Logger.debug("Rule " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find rule with id [$id]"
        ))
      }
    })

    withErrorHandling(future, s"Cannot retrieve rule with id [$id]")
  }

  def createOrUpdate(version: Int, id: String, preview: Boolean) = Action.async (parse.json) { request =>
    Json.fromJson[Rule](request.body).map { rule =>
      try {
        val ruleDoc = solrServer.binder.toSolrInputDocument(rule)
        val update = new AsyncUpdateRequest()
        update.add(ruleDoc)
        withRuleCollection(update, preview, request.acceptLanguages)

        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.RuleController.findById(id), request)))
        })

        withErrorHandling(future, s"Cannot store Rule with id [$id]")
      }
      catch {
        case e : BindingException =>
          Future.successful(BadRequest(Json.obj(
            "message" -> "Illegal Rule fields",
            "detail" -> e.getMessage)))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Illegal Rule fields")))
    }.get
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = ContextAction.async (parse.json(maxLength = 1024 * 2000)) { 
    implicit context => request =>
      Json.fromJson[RuleList](request.body).map { ruleList =>
      val rules = ruleList.rules
        if (rules.length > MaxRuleIndexBatchSize) {
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Exceeded number of Rules. Maximum is $MaxRuleIndexBatchSize")))
        } else {
          val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)
          val storage = withNamespace(storageFactory)
          
          val future = categoryService.getTaxonomy(storage, context.isPreview).flatMap(taxonomy => {
            rules.foreach(rule => {
                if (rule.category != null && rule.category.isDefined) {
                    val categoryPaths = rule.getCategory.flatMap({ categoryId => ruleCategoryPathMapper(categoryId, taxonomy) })
                    rule.category = Option(categoryPaths)
                }
                update.add(solrServer.binder.toSolrInputDocument(rule))
              }
            )
            update.process(solrServer).map( response => {
                    Created(Json.obj(
                      "locations" -> JsArray(
                        rules map (b => Json.toJson(routes.RuleController.findById(b.id.get).url))
                      )))
            }) 
          })
          
          withErrorHandling(future, s"Cannot store Rules with ids [${rules map (_.id.get) mkString ","}]")
        }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields",
        "detail"  -> JsError.toFlatJson(e))))
    }.get
  }

  /**
   * Helper method that maps a given category id to it's corresponding 
   * set of category paths using both, the namePath and the idPath formats
   * @param The id of the category to lookup in the taxonomy
   * @param the object holding the taxonomy
   */
  private def ruleCategoryPathMapper(category: String, taxonomy: Taxonomy) : Seq[String] = {
    val categoryObj = taxonomy.get(category)
    if ("__all__".equals(category)) {
      Seq("__all__")
    } else if(categoryObj != null && categoryObj.isDefined) {
      val paths = categoryService.getPaths(categoryObj.get, taxonomy)
      paths.map(f =>  f.idPath) ++ paths.map(f => f.namePath)
    } else {
      Seq.empty
    }
  }
  
  /**
   * Post method for the rules endpoint. Will send commit or rollback to Solr accordingly.
   * @param commit true if a commit should be done.
   * @param rollback true if a rollbac should be done.
   */
  def commitOrRollback(preview: Boolean, commit: Boolean, rollback: Boolean) = Action.async { request =>
    if(commit == rollback) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"commit and boolean can't have the same value.")))
    }
    else {
      val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)

      if(commit) {
        update.setAction(ACTION.COMMIT, false, false, false)
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "commit success"))
        })

        withErrorHandling(future, s"Cannot commit rules.")
      }
      else {
        update.rollback
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "rollback success"))
        })

        withErrorHandling(future, s"Cannot rollback rules.")
      }
    }
  }

  /**
   * Delete method that remove all rules matching a given query.
   * @param query is the query used to delete rules, default is *:*
   */
  def deleteByQuery(preview: Boolean, query: String) = Action.async { request =>
    val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)

    update.deleteByQuery(query)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      Ok (Json.obj(
        "message" -> "delete success"))
    })

    withErrorHandling(future, s"Cannot delete rules.")
  }
}
