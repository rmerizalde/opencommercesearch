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

import java.util

import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.beans.Field
import org.apache.commons.lang.StringUtils
import org.jongo.marshall.jackson.oid.Id

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.opencommercesearch.search.suggester.IndexableElement

/**
 * A brand model
 *
 * @param id is the system id of the brand
 * @param name is the display name of the brand
 * @param logo is the URL for the brand's logo
 *
 * @author rmerizalde
 */
case class Brand(
   @Id var id: Option[String], 
   @JsonProperty("name") var name: Option[String], 
   @JsonProperty("logo") var logo: Option[String], 
   @JsonProperty("url") var url: Option[String],
   @JsonProperty("sites") var sites: Option[Seq[String]]) extends IndexableElement {

  def getId : String = this.id.getOrElse(null)
  
  def getName : String = this.name.getOrElse(null)

  def getUrl: String = url.getOrElse(null)

  override def source = "brand"

  override def toJson : JsValue = { Json.toJson(this) }

  def getSites : Seq[String] = {
    this.sites.getOrElse(Seq.empty[String])
  }

  def toDocument(feedTimestamp: Long) : SolrInputDocument = {
    val doc = new SolrInputDocument()

    for(i <- id) {
      doc.setField("id", i)
    }

    for(n <- name) {
      doc.setField("name", n)
    }

    for(l <- logo) {
      doc.setField("logo", l)
    }

    for(v <- url) {
      doc.setField("url", v)
    }

    for(s <- sites) {
      s.foreach { site =>
        doc.addField("siteId", site)
      }
    }

    doc.setField("feedTimestamp", feedTimestamp)

    doc
  }

  def getNgramText : String = {
    name.getOrElse(null)
  }

  def getType : String = {
    "brand"
  }
}

object Brand {

  implicit val readsBrand = Json.reads[Brand]
  implicit val writesBrand = Json.writes[Brand]

  def fromDocument(doc : SolrDocument) : Brand = {
    val id = doc.get("id").asInstanceOf[String]
    val name = doc.get("name").asInstanceOf[String]
    val logo = doc.get("logo").asInstanceOf[String]
    val url = doc.get("url").asInstanceOf[String]
    val sites = doc.get("siteId").asInstanceOf[Seq[String]]

    new Brand(Option.apply(id), Option(name), Option(logo), Option(url), Option(sites))
  }
}

/**
 * Represents a list of brands
 *
 * @param brands are the brands in the list
 */
case class BrandList(brands: List[Brand], feedTimestamp: Long) {

  def toDocuments : util.List[SolrInputDocument] = {
    val docs = new util.ArrayList[SolrInputDocument](brands.length)

    for (brand <- brands) {
      docs.add(brand.toDocument(feedTimestamp))
    }
    docs
  }

}

object BrandList {
  implicit val readsBrands = Json.reads[BrandList]
}



