package org.opencommercesearch.api

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

import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, Request, Call}

/**
 * @todo figure out if there a way to use Call.absoluteUrl with play.api.mvc.Request
 */
object Util {

  /**
   * Transform the given call to an absolute URL.
   */
  def absoluteURL[T](call: Call, request: Request[T], secure: Boolean = false) : String = {
    s"http${if (secure) "s" else ""}://${request.host}${call.url}"
  }
}
