package org.opencommercesearch.search.suggester

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

import scala.concurrent.Future
import scala.collection.mutable
import scala.collection.JavaConversions._

import org.opencommercesearch.api.Global._
import org.opencommercesearch.search.Element
import org.apache.solr.client.solrj.{SolrQuery, AsyncSolrServer}



/**
 * @author rmerizalde
 */
class GroupingSuggester[E <: Element](val typeToClass : Map[String, Class[_ <: Element]]) extends Suggester[E] {

  protected def searchInternal(q: String, site: String, server: AsyncSolrServer): Future[Seq[E]] = {
    val query = new SolrQuery(q)
    query.setParam("collection", SuggestCollection)
      .setFields("id", "userQuery")
      .setFilterQueries(s"siteId:$site")
      .set("group", true)
      .set("group.ngroups", false)
      .set("group.field", "type")
      .set("group.facet", false)
      .set("group.limit", 10)
      .set("defType", "edismax")
      .set("qf", "userQuery ngrams")

    solrServer.query(query).map( response => {
      val elements = new mutable.ListBuffer[E]

      if (response.getGroupResponse != null) {
        for (command <- response.getGroupResponse.getValues) {
          for (group <- command.getValues) {
            val `type` = if (group.getGroupValue == null) "userQuery" else group.getGroupValue
            val clazz = typeToClass.get(`type`).getOrElse(null)

            if (clazz != null) {
              group.getResult.map(doc => {
                val element = server.binder.getBean(clazz, doc).asInstanceOf[E]
                for (id <- element.id) {
                  if (id.startsWith(`type`) && id.size > (`type`.size + 1)) {
                    element.id = Some(id.substring(`type`.size + 1))
                  }
                }
                elements += element
              })
            }
          }
        }
      }
      elements
    })
  }
}
