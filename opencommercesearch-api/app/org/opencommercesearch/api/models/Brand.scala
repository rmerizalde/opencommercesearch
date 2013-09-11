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

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.util

import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrInputDocument


/**
 * A brand model
 *
 * @param id is the system id of the brand
 * @param name is the display name of the brand
 * @param logo is the URL for the brand's logo
 */
case class Brand(id: Option[String], name: Option[String], logo: Option[String]) {

  def toDocument : SolrInputDocument = {
    val doc = new SolrInputDocument()

    if (id.isDefined) {
      doc.setField("id", id.get)
    }
    if (name.isDefined) {
      doc.setField("name", name.get)
    }
    if (logo.isDefined) {
      doc.setField("logo", logo.get)
    }
    doc
  }
}


object Brand {

  implicit val readsBrand: Reads[Brand] = (
    (__ \ "id").readNullable[String] ~
    (__ \ "name").readNullable[String] ~
    (__ \ "logo").readNullable[String]
  ) (Brand.apply _)

  implicit val writesBrand : Writes[Brand] = (
    (__ \ "id").writeNullable[String] ~
    (__ \ "name").writeNullable[String] ~
    (__ \ "logo").writeNullable[String]
  ) (unlift(Brand.unapply))

  def fromDocument(doc : SolrDocument) : Brand = {
    val id = doc.get("id").asInstanceOf[String]
    val name = doc.get("name").asInstanceOf[String]
    val logo = doc.get("logo").asInstanceOf[String]

    new Brand(Option.apply(id), Option.apply(name), Option.apply(logo))
  }
}



/**
 * Represents a list of brands
 *
 * @param brands are the brands in the list
 */
case class BrandList(brands: List[Brand]) {

  def toDocuments : util.List[SolrInputDocument] = {
    val docs = new util.ArrayList[SolrInputDocument](brands.length)

    for (brand <- brands) {
      docs.add(brand.toDocument)
    }
    docs
  }

}

object BrandList {
  implicit val readsBrands = Json.reads[BrandList]
}



