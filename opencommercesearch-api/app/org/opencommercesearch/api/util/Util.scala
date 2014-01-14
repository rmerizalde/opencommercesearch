package org.opencommercesearch.api.util

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

import play.api.mvc.{Request, Call}
import java.util.{MissingResourceException, ResourceBundle}
import org.opencommercesearch.api.common.FilterQuery
import org.apache.commons.lang3.StringUtils
import play.api.i18n.Messages

/**
 * @todo figure out if there a way to use Call.absoluteUrl with play.api.mvc.Request
 */
object Util {


  val RESOURCE_CRUMB = "crumb"
  val ResourceInRange = "inrange"
  val ResourceBefore = "before"
  val ResourceAfter = "after"

  //val resources = ResourceBundle.getBundle("org.opencommercesearch.CSResources.properties")

  def createPath(filterQueries: Array[FilterQuery], skipFilter: FilterQuery): String = {
    createPath(filterQueries, skipFilter, null)
  }

  def createPath(filterQueries: Array[FilterQuery], skipFilter: FilterQuery, replacementFilterQuery: String): String = {
    if (filterQueries == null) {
      StringUtils.EMPTY
    } else {
      val b = new StringBuilder()

      for (filterQuery <- filterQueries) {
        if (!filterQuery.equals(skipFilter)) {
          b.append(filterQuery.toString).append(FilterQuery.PathSeparator)
        } else if (replacementFilterQuery != null) {
          b.append(replacementFilterQuery).append(FilterQuery.PathSeparator)
        }
      }
      if (b.length > 0) {
        b.setLength(b.length - 1)
      }
      b.toString()
    }
  }

  private def loadResource(key: String) : String = {
    if (Messages.isDefinedAt(key)) {
      Messages(key)
    } else {
      null
    }
  }

  def getRangeName(fieldName: String, key: String, value1: String, value2: String, defaultName: String): String = {
    var resource: String = null
    val resourceKey = "facet.range." + fieldName + "." + key

    // First try to find if there's a specific resource for the value
    resource = loadResource(resourceKey + "." + value1)
    if (resource == null) {
      resource = loadResource(resourceKey + "." + value2)
    }

    if (resource == null) {
      resource = loadResource(resourceKey)
    }

    if (resource == null) {
      if (defaultName != null) {
        return defaultName
      }
      resource = "$[v1]-$[v2]"
    }

    var rangeName = StringUtils.replace(resource, "$[v1]", if (value1 == null) "" else value1)
    rangeName = StringUtils.replace(rangeName, "$[v2]", if (value2 == null) "" else value2)
    rangeName
  }

  def getRangeName(fieldName: String, expression: String): String = {
    if (expression.startsWith("[") && expression.endsWith("]")) {
      val parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ")
      if (parts.length == 2) {
        var key = ResourceInRange
        if ("*".equals(parts(0))) {
          key = ResourceBefore
        } else if ("*".equals(parts(1))) {
          key = ResourceAfter
        }
        return getRangeName(fieldName, key, parts(0), parts(1), null)
      }
    }
    expression
  }

  def getRangeBreadCrumb(fieldName: String, expression: String): String = {
    getRangeBreadCrumb(fieldName, expression, null)
  }

  def getRangeBreadCrumb(fieldName: String, expression: String, defaultCrumb: String): String = {
    if (expression.startsWith("[") && expression.endsWith("]")) {
      val parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ")
      if (parts.length == 2) {
        return getRangeName(fieldName, RESOURCE_CRUMB, parts(0), parts(1), defaultCrumb)
      }
    }
    expression
  }

  /**
   * Transform the given call to an absolute URL.
   */
  def absoluteURL[T](call: Call, request: Request[T], secure: Boolean = false) : String = {
    s"http${if (secure) "s" else ""}://${request.host}${call.url}"
  }
}
