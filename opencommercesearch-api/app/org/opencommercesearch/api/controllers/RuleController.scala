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

import javax.ws.rs.{PathParam, QueryParam}

import com.wordnik.swagger.annotations._
import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.beans.BindingException
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.models.{Rule, RuleList}
import org.opencommercesearch.api.util.Util._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray, JsError, Json}
import play.api.mvc._

import scala.concurrent.Future

@Api(value = "rules", basePath = "/api-docs/rules", description = "Rule API endpoints.")
object RuleController extends BaseController {

  @ApiOperation(value = "Searches Rules", notes = "Returns information for a given rule", response = classOf[Rule], httpMethod = "GET")
  @ApiResponses(Array(new ApiResponse(code = 404, message = "Rule not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "fields", value = "Comma delimited field list", defaultValue = "name", required = false, dataType = "string", paramType = "query")
  ))
  def findById(version: Int,
               @ApiParam(value = "A rule id", required = true)
               @PathParam("id")
               id: String,
               @ApiParam(defaultValue="false", allowableValues="true,false", value = "Display preview results", required = false)
               @QueryParam("preview")
               preview: Boolean) = Action.async { implicit request =>

    val ruleIds = StringUtils.split(id, ",")
    val storage = withNamespace(storageFactory, preview)
    val storageFuture = storage.findRules(ruleIds, fieldList(allowStar = true))

    val future = storageFuture map { rules =>
      if(rules != null && rules.nonEmpty) {
        Logger.debug("Found rules " + id)
        Ok(Json.obj(
          "rules" -> rules))
      } else {
        Logger.debug("Rules " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find rules with ids [$id]"
        ))
      }
    }

    withErrorHandling(future, s"Cannot retrieve rule with id [$id]")
  }

  def createOrUpdate(version: Int, id: String, preview: Boolean) = Action.async (parse.json) { implicit request =>
    Json.fromJson[Rule](request.body).map { rule =>
      try {
        val ruleDoc = solrServer.binder.toSolrInputDocument(rule)
        val update = new AsyncUpdateRequest()
        update.add(ruleDoc)
        withRuleCollection(update, preview, request.acceptLanguages)

        val storage = withNamespace(storageFactory, preview)
        val storageFuture = storage.saveRule(rule)
        val searchFuture: Future[UpdateResponse] = update.process(solrServer)

        val future: Future[SimpleResult] = storageFuture zip searchFuture map { case (storageResult, searchResponse) =>
          Created.withHeaders((LOCATION, absoluteURL(routes.RuleController.findById(id), request)))
        }

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

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action.async (parse.json(maxLength = 1024 * 2000)) { implicit request =>
    Json.fromJson[RuleList](request.body).map { ruleList =>
      val rules = ruleList.rules
      try {
        if (rules.length > MaxRuleIndexBatchSize) {
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Exceeded number of Rules. Maximum is $MaxRuleIndexBatchSize")))
        } else {
          val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)
          rules map { rule =>
              update.add(solrServer.binder.toSolrInputDocument(rule))
          }

          val storage = withNamespace(storageFactory, preview)
          val storageFuture = storage.saveRule(ruleList.rules:_*)
          val searchFuture: Future[UpdateResponse] = update.process(solrServer)

          val future = storageFuture zip searchFuture map { case (storageResponse, response) =>
            Created(Json.obj(
              "locations" -> JsArray(
                rules map (b => Json.toJson(routes.RuleController.findById(b.id.get).url))
              )))
          }

          withErrorHandling(future, s"Cannot store Rules with ids [${rules map (_.id.get) mkString ","}]")
        }
      }
      catch {
        case e : BindingException =>
          //Handle bind exceptions
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Illegal Rule fields [${rules map (_.id.get) mkString ","}] ",
            "detail" -> e.getMessage)))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields",
        "detail"  -> JsError.toFlatJson(e))))
    }.get
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
