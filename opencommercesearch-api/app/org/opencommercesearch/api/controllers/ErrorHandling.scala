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
import scala.concurrent.Future
import org.apache.solr.client.solrj.beans.BindingException

trait ErrorHandling {
  self: Controller =>

  def withErrorHandling(f: Future[SimpleResult], message: String) : Future[SimpleResult]  = {
    f.recover { 
     case e : BindingException =>
       //Handle bind exceptions
       BadRequest(Json.obj(
         "message" -> ("Illegal fields: " + message),
         "detail" -> e.getMessage))
      case t : Throwable =>
        Logger.error(message, t)
        InternalServerError(Json.obj(
          // @Todo refine developer messages ??
          "message" -> message))
    }
  }
}
