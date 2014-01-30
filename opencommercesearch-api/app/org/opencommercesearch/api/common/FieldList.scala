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

import play.api.mvc.{AnyContent, Controller, Request}
import org.apache.solr.client.solrj.SolrQuery
import org.apache.commons.lang3.StringUtils

/**
 * This trait provides subclasses with functionality to parse the list
 * of fields that should be return in the response
 *
 * @author rmerizalde
 */
trait FieldList {

  /**
   * @deprecated
   */
  def withFields(query: SolrQuery, fields: Option[String]) : SolrQuery = {
    for (f <- fields) {
      if (f.size > 0) { query.setFields(fields.get.split(','): _*) }
    }
    query
  }

  /**
   * Return a sequence with list of fields to return from storage.
   * @param request is the implicit request
   * @param allowStar Whether or not star ("*") should be allowed. If true then start ("*") is kept on the field list, otherwise is removed. By default is false.
   * @param fieldsFieldName The fields field name to look for on the request. By default is "fields"
   * @tparam R type of the request
   * @return a sequence with the field names
   */
  def fieldList[R](allowStar : Boolean = false, fieldsFieldName: String = "fields")(implicit request: Request[R]) : Seq[String] = {
    val fields = request.getQueryString(fieldsFieldName)
    var fieldSeparators = ","

    if(!allowStar) {
      fieldSeparators += "*"
    }

    StringUtils.split(fields.getOrElse(StringUtils.EMPTY), fieldSeparators)
  }
}
