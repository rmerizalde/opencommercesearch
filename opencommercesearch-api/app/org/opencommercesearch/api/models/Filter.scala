package org.opencommercesearch.api.models

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

import play.api.libs.json.Json

import org.opencommercesearch.api.common.FilterQuery

/**
 * A filter class represent a bucket in a facet. Each bucket/filter has a name, count of documents
 * and a filter query. In addition, a filter has flag to indicate if the bucket has been selected already.
 * Finally, the filter has filter queries which include the filter query for this query to select/un-select the bucket
 * plus the filters of all other facets the user has selected already.
 *
 * @author rmerizalde
 */
case class Filter(
  var name: Option[String],
  var count: Option[Long],
  var filterQueries: Option[String],
  var filterQuery: Option[String],
  var isSelected: Option[Boolean]) {

  def setSelected(fieldName: String, expression: String, filterQueries: Array[FilterQuery]): Unit = {
    if (filterQueries != null) {
      val query = filterQueries.find(query => query.fieldName.equals(fieldName) && query.unescapeExpression.equals(FilterQuery.unescapeQueryChars(expression)))

      for (q <- query) {
        isSelected = Some(true)
      }
    }
  }
}

object Filter {
  implicit val readsFilter = Json.reads[Filter]
  implicit val writesFilter = Json.writes[Filter]
}
