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
import play.api.mvc.{Controller, SimpleResult}
import play.api.Logger
import play.api.libs.json.Json

import org.apache.solr.common.SolrException
import org.apache.solr.search.SyntaxError


import scala.concurrent.Future

trait ErrorHandling {
  self: Controller =>

  def withErrorHandling(f: Future[SimpleResult], message: String) : Future[SimpleResult]  = {
    def internalServerError(t: Throwable) = {
      Logger.error(message, t)
      InternalServerError(Json.obj(
        "message" -> message
      )).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }

    def badRequest(t: Throwable) = {
      val syntaxErrorMessage = "Invalid query or filter queries"
      Logger.error(syntaxErrorMessage, t)
      BadRequest(Json.obj(
        "message" -> syntaxErrorMessage
      )).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }

    f.recover {
      case e: SolrException =>
        println(e.getCause)
        e.getCause match {
          case syntaxError: SyntaxError =>
            badRequest(syntaxError)
          case t: Throwable =>
            internalServerError(t)
          case _ =>
            if (e.getMessage.contains("org.apache.solr.search.SyntaxError")) {
              badRequest(e)
            } else {
              internalServerError(e)
            }
        }
      case t: Throwable =>
        internalServerError(t)
    }
  }
}
