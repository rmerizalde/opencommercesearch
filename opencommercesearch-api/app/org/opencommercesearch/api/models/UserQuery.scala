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

import org.apache.solr.client.solrj.beans.Field
import org.opencommercesearch.search.Element
import scala.beans.BeanProperty
import scala.annotation.meta.beanSetter

case class UserQuery (var userQuery: Option[String], var count: Option[Int]) extends Element {

  @(Field @beanSetter) @BeanProperty
  var id: Option[String] = _

  def this() = this(None, None)

  override def source = "userQuery"

  @Field
  def setUserQuery(userQuery: String) : Unit = {
    this.userQuery = Option.apply(userQuery)
  }

  @Field
  def setCount(count: Int) : Unit = {
    this.count = Option.apply(count)
  }

}

object UserQuery {
  implicit val readsUserQuery = Json.reads[UserQuery]
  implicit val writesUserQuery = Json.writes[UserQuery]
}