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

import play.api.libs.json.{JsValue, Json}

/**
 * @author jmendez
 */


object TestElement {
  implicit val readsTestElement = Json.reads[TestElement]
  implicit val writesTestElement = Json.writes[TestElement]
}

case class TestElement(var id: Option[String]) extends IndexableElement {
  override def getId: String = "testId"

  override def getType: String = "test"

  override def getNgramText: String = "Some significative text"

  override def getSites: Seq[String] = Seq("myTestSite")

  override def source: String = "testSource"

  override def toJson: JsValue = Json.toJson(this)
}