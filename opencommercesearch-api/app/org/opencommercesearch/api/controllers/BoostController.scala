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
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.models.PageBoost
import org.opencommercesearch.api.util.Timer
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

@Api(value = "boosts", basePath = "/api-docs/boosts", description = "Boost API endpoints")
object BoostController extends Controller {

  val database = storageFactory.getDatabase("core")
  val collection = database[BSONCollection]("boosts")


  Logger.info(s"Setting up indexes for boost collection")
  collection.indexesManager.ensure(new Index(key = Seq(("feedTimestamp", IndexType.Ascending)), name = Some("feed_timestamp_idx"), sparse = true))

  @ApiOperation(value = "Searches product boots", notes = "Returns product boosts for a given page type", response = classOf[Product], httpMethod = "GET")
  @ApiResponses(value = Array(new ApiResponse(code = 404, message = "Product not found")))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "offset", value = "Offset in the boost list", defaultValue = "0", required = false, dataType = "int", paramType = "query"),
    new ApiImplicitParam(name = "limit", value = "Maximum number of boosts", defaultValue = "10", required = false, dataType = "int", paramType = "query")
  ))
  def findById(version: Int,
    @ApiParam(value = "A page id", required = true)
    @PathParam("id")
    id: String) = Action.async { request =>

    val timer = new Timer()
    val query = BSONDocument("_id" -> id)

    def response(page: Option[PageBoost]) = page match {
      case Some(p) =>
        Ok(Json.obj(
          "metadata" -> Json.obj("time" -> timer.stop()),
          "boosts" -> Json.toJson(p.boosts)))
      case None =>
        NotFound(Json.obj("metadata" -> Json.obj("time" -> timer.stop())))
    }

    def errorResponse(throwable: Throwable) = {
      Logger.error("Cannot retrieve boosts", throwable)
      InternalServerError(Json.obj(
        "metadata" -> Json.obj("time" -> timer.stop()),
        "message" -> "Cannot retrieve boosts"
      ))
    }

    collection.find(query)
      .one[PageBoost]
      .map(page => response(page))
      .recover({ case t => errorResponse(t) })
  }

  @ApiOperation(value = "Deletes boosts", notes = "Deletes boosts that were not updated in a given feed", httpMethod = "DELETE")
  def deleteByTimestamp(
      version: Int = 1,
      @ApiParam(value = "The feed timestamp. All boosts with a different timestamp are deleted", required = true)
      @QueryParam("feedTimestamp")
      feedTimestamp: Long) = Action.async { request =>
    val timer = new Timer()

    collection.remove(BSONDocument("feedTimestamp" -> BSONDocument("$ne" -> feedTimestamp))) map { res =>
      if (res.ok) {
        Ok(Json.obj("metadata" -> Json.obj("time" -> timer.stop())))
      } else {
        InternalServerError(Json.obj(
          "metadata" -> Json.obj("time" -> timer.stop()),
          "message" -> s"Cannot delete boosts by feed timestamp ($feedTimestamp)"
        ))
      }
    }
  }


  def bulkCreateOrUpdate(version: Int) = Action.async(parse.json(maxLength = 1024 * 2000)) { request =>
    val timer = new Timer()

    Json.fromJson[Seq[PageBoost]](request.body) map { pages =>
      if (pages.size > MaxBoostBatchSize) {
        Future.successful(BadRequest(Json.obj(
          "metadata" -> Json.obj("time" -> timer.stop()),
          "message" -> s"Exceeded number of boosts. Maximum is $MaxBoostBatchSize")))
      } else {
        Future.sequence(pages.map(p => collection.save(p))) map { lastErrors =>
          val failures = lastErrors.filter(le => !le.ok)
          if (failures.size > 0) {
            for (failure <- failures) {
              Logger.error(s"Cannot save boosts. ${failure.stringify}")
            }

            InternalServerError(Json.obj(
              "metadata" -> Json.obj("time" -> timer.stop()),
              "message" -> s"Cannot stored boosts. Found ${failures.size} failures"
            ))
          } else {
            Created
          }
        }
      }
    } recoverTotal {
      case e: JsError =>
        Logger.error("Cannot create/update boosts " + e.errors)
        Future.successful(BadRequest(Json.obj(
          "metadata" -> Json.obj("time" -> timer.stop()),
          "message" -> "Missing required fields"
        )))
    }
  }
}
