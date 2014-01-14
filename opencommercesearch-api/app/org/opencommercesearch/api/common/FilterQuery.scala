package org.opencommercesearch.api.common

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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.HashCodeBuilder

/**
 * This class represents represents a filter query. A filter consist of a field
 * name and a expression.
 *
 * @author rmerizalde
 *
 */
case class FilterQuery(
  fieldName: String,
  expression: String) {

  override def toString: String = {
    fieldName + FilterQuery.Seperator + expression
  }

  override def equals(o: Any): Boolean = {
    if (o == null || this.getClass != o.getClass) {
      false
    } else {
      val other = o.asInstanceOf[FilterQuery]
      fieldName.equals(other.fieldName) && expression.equals(other.expression)
    }
  }

  override def hashCode(): Int = {
    new HashCodeBuilder()
      .append(fieldName)
      .append(expression)
      .toHashCode
  }

  def unescapeExpression = {
    FilterQuery.unescapeQueryChars(expression)
  }
}

object FilterQuery {
  val Seperator = ":"
  val FilterQueryParts = 2
  val PathSeparator = "|"

  def apply(filterQuery: String): FilterQuery = {
    val parts = StringUtils.split(filterQuery, Seperator, FilterQueryParts)

    if (parts.length != FilterQueryParts) {
      throw new IllegalArgumentException("Invalid filter query: " + filterQuery)
    }
    new FilterQuery(parts(0), parts(1))
  }

  def parseFilterQueries(filterQueries: String): Array[FilterQuery] = {
    val array = StringUtils.split(filterQueries, PathSeparator)

    if (array != null) {
      val output = new Array[FilterQuery](array.length)

      for (i <- 0 to array.length - 1) {
        output(i) = FilterQuery(array(i))
      }

      output
    } else {
      null
    }
  }

  // @TODO move this to org.apache.solr.client.solrj.util.ClientUtils ??
  def unescapeQueryChars(s: String): String = {
    val sb = new StringBuilder()
    for (i <- 0 to s.length() - 1) {
      var c = s.charAt(i)

      if (c != '\\') {
        sb.append(c)
      } else {
        if ((i + 1) < s.length()) {
          c = s.charAt(i + 1)
          // These characters are part of the query syntax and if escaped
          // lets skip the escape char
          if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^'
            || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~' || c == '*'
            || c == '?' || c == '|' || c == '&' || c == ';' || c == '/' || Character.isWhitespace(c)) {
            // NOOP
          } else {
            sb.append('\\')
          }
        } else {
          sb.append('\\')
        }
      }
    }
    sb.toString()
  }
}