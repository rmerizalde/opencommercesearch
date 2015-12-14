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


import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.joda.time.DateTimeZone
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.common.{ContentPreview, FacetQuery, FieldList}
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.Element
import org.opencommercesearch.search.collector.{MultiSourceCollector, SimpleCollector}
import org.opencommercesearch.search.suggester.CatalogSuggester
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Result, _}

import scala.collection.convert.Wrappers.JIterableWrapper
import scala.concurrent.Future

/**
 * This class provides common functionality for all controllers
 *
 * @todo refactor other functionality to this controller
 *
 * @author rmerizalde
 */
class BaseController extends Controller with ContentPreview with FieldList with FacetQuery with Pagination with ErrorHandling with Retrying {

  private val timeZoneCode = "GMT"
  private val suggester = new CatalogSuggester[Element]

  private val df: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss '" + timeZoneCode + "'").withLocale(java.util.Locale.ENGLISH).withZone(DateTimeZone.forID(timeZoneCode))

  protected def withCorsHeaders(result: Result) : Result = {
    result.withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
  }

  protected def withCacheHeaders(result: Result, ids: String)(implicit request: Request[AnyContent]) : Result = {
    val lastModified = df.print(System.currentTimeMillis())
    withProductIds(result, ids, request).withHeaders((LAST_MODIFIED -> lastModified))
  }

  protected def withCacheHeaders(result: Result, ids: Iterable[String])(implicit request: Request[AnyContent]) : Result = {
    val lastModified = df.print(System.currentTimeMillis())
    withProductIds(result, ids, request).withHeaders((LAST_MODIFIED -> lastModified))
  }

  private def withProductIds(result: Result, ids: String, request: Request[AnyContent]) = request.headers.get("X-Cache-Ids") match {
    case Some(s) => ids match {
      case "" =>  result
      case _ => result.withHeaders(("X-Cache-Product-Ids", ids))
    }
    case None => result
  }

  private def withProductIds(result: Result, ids: Iterable[String], request: Request[AnyContent]) = request.headers.get("X-Cache-Ids") match {
    case Some(s) => if (ids.isEmpty) result else result.withHeaders(("X-Cache-Product-Ids", ids.mkString(",")))
    case None => result
  }

  protected def findSuggestionsFor(typeName: String, query: String, site: String, facets: Boolean = false)(implicit context: Context): Future[Result] = {
    val startTime = System.currentTimeMillis()

    if (query == null || query.length < 2) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"At least $MinSuggestQuerySize characters are needed to make suggestions"
      )))
    } else {
      val collector = new MultiSourceCollector[Element]
      collector.add(typeName, new SimpleCollector[Element])

      val future = suggester.search(query, site, collector, solrServer, facets) map { c =>
       Ok(Json.obj(
          "metadata" -> Json.obj(
             "found" -> c.size(),
             "time" -> (System.currentTimeMillis() - startTime)),
          "suggestions" -> (c.elements() map { el => el.toJson })
        ))
      }

      withErrorHandling(future, s"Cannot suggest $typeName for [$query]")
    }
  }

  protected def findSuggestionsFor[T](clazz: Class[T], typeName: String, query: SolrQuery)(implicit req: Request[AnyContent], c: Writes[T]): Future[Result] = {

    val startTime = System.currentTimeMillis()
    val solrQuery = withPagination(withFields(query, req.getQueryString("fields")))

    if (query == null || query.getQuery.length < 2) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"At least $MinSuggestQuerySize characters are needed to make suggestions"
      )))
    } else {
      val future = solrServer.query(solrQuery).map(response => {
        val docs = response.getResults
        Ok(Json.obj(
          "metadata" -> Json.obj(
            "found" -> docs.getNumFound,
            "time" -> (System.currentTimeMillis() - startTime)),
          "suggestions" -> JIterableWrapper(docs).map(doc => solrServer.binder.getBean(clazz, doc))
        ))
      })

      withErrorHandling(future, s"Cannot suggest $typeName  for [${query.getQuery}]")
    }
  }

  /**
   * Delete method that removes all docs matching a given query.
   * @param query is the query used to delete docs, default is *:*
   * @param update is the update request used to delete docs. Child classes should set the collection the request should use.
   * @param typeName name of the item type to delete
   */
  protected def deleteByQuery(query: String, update: AsyncUpdateRequest, typeName: String) : Future[Result] = {
    update.deleteByQuery(query)

    val future: Future[Result] = update.process(solrServer).map( response => {
      NoContent
    })

    withErrorHandling(future, s"Cannot delete $typeName")
  }

  /**
   * Will send commit or rollback to Solr accordingly.
   * @param commit true if a commit should be done.
   * @param rollback true if a rollback should be done.
   * @param update is the update request used to commit or rollback docs. Child classes should set the collection the request should use.
   * @param typeName name of the item type to commit or rollback
   */
  def commitOrRollback(commit: Boolean, rollback: Boolean, update: AsyncUpdateRequest, typeName: String) : Future[Result] = {
    if(commit == rollback) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"commit and boolean can't have the same value.")))
    }
    else {
      if(commit) {
        update.setAction(ACTION.COMMIT, false, false, false)
        val future: Future[Result] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "commit success"))
        })

        withErrorHandling(future, s"Cannot commit $typeName")
      }
      else {
        update.rollback
        val future: Future[Result] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "rollback success"))
        })

        withErrorHandling(future, s"Cannot rollback $typeName")
      }
    }
  }

  object ContextAction {
    import play.api.Play.current
    import play.api.i18n.Lang.preferred

    /**
     * Action composition for async actions that require a context and request
     *
     * @param block the action code
     * @return a future Result
     */
    final def async(block: Context => Request[AnyContent] => Future[Result]): Action[AnyContent] = async(BodyParsers.parse.anyContent)(block)

    /**
     * Action composition for async actions that require a context and request
     * @param bodyParser is the body parser
     * @param block the action code
     * @tparam A is the content type
     * @return
     */
    final def async[A](bodyParser: BodyParser[A])(block: Context => Request[A] => Future[Result]): Action[A] = Action.async(bodyParser) { implicit request =>
      val preview = request.getQueryString("preview").getOrElse(false)
      val context = Context("true".equals(preview), preferred(request.acceptLanguages))

      block(context)(request)
    }
  }



}
