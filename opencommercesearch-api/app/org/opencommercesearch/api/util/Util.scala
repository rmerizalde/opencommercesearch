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
import java.util._
import org.opencommercesearch.api.common.FilterQuery
import org.apache.commons.lang3.StringUtils
import play.api.i18n.Messages
import org.apache.solr.util.DateMathParser
import org.joda.time.DateTime
import org.joda.time.Days
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.regex.Pattern

/**
 * @todo figure out if there a way to use Call.absoluteUrl with play.api.mvc.Request
 */
object Util {

  val RESOURCE_CRUMB = "crumb"
  val ResourceInRange = "inrange"
  val ResourceBefore = "before"
  val ResourceAfter = "after"
  val Now = "NOW"
  val SolrDatePattern = Pattern.compile("HOUR|DAY|MONTHS|YEARS|NOW")
  val DateFormatterISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    
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
  
  @throws(classOf[ParseException])
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
    
    var rangeName = defaultName
    if (resource.contains("$[days]")) {
      val days = daysBetween(value1, value2)
      rangeName = StringUtils.replace(resource, "$[days]", String.valueOf(days))
    } else{
      rangeName = StringUtils.replace(resource, "$[v1]", if (value1 == null) StringUtils.EMPTY else value1)
      rangeName = StringUtils.replace(rangeName, "$[v2]", if (value2 == null) StringUtils.EMPTY else value2)	
    }
    rangeName
  }
  
  @throws(classOf[ParseException])
  def getRangeName(fieldName: String, expression: String): String = {
    if (expression.startsWith("[") && expression.endsWith("]")) {
      val parts = StringUtils.splitByWholeSeparator(expression.substring(1, expression.length() - 1), " TO ")
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

  @throws(classOf[ParseException])
  def getRangeBreadCrumb(fieldName: String, expression: String): String = {
    getRangeBreadCrumb(fieldName, expression, null)
  }
  
  @throws(classOf[ParseException])
  def getRangeBreadCrumb(fieldName: String, expression: String, defaultCrumb: String): String = {
    if (expression.startsWith("[") && expression.endsWith("]")) {
      val parts = StringUtils.splitByWholeSeparator(expression.substring(1, expression.length() - 1), " TO ")
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
  
  @throws(classOf[ParseException])
  def parseDate(value: String, dmp: DateMathParser) : Date = {
    if(SolrDatePattern.matcher(value).find()) {
      dmp.parseMath(StringUtils.remove(value, Now))
    } else {
      DateFormatterISO8601.parse(value)
    }
  }
  
  @throws(classOf[ParseException])
  def daysBetween(from: String, to: String) : Int = {
    val dmp = new DateMathParser(TimeZone.getDefault, Locale.getDefault)
    var fromDate = parseDate(from, dmp)
    var toDate = parseDate(to, dmp)
    Days.daysBetween(new DateTime(fromDate), new DateTime(toDate)).getDays()
  }
}
