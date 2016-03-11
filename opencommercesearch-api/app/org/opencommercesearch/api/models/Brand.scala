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

import java.util

import org.apache.solr.common.{SolrDocument, SolrInputDocument}
import org.opencommercesearch.search.suggester.IndexableElement
import play.api.libs.json._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

object Brand {

  implicit val readsBrand = Json.reads[Brand]
  implicit val writesBrand = Json.writes[Brand]

  implicit object BrandWriter extends BSONDocumentWriter[Brand] {
    import org.opencommercesearch.bson.BSONFormats._
    import reactivemongo.bson._

    def write(brand: Brand): BSONDocument = BSONDocument(
      "_id" -> brand.id,
      "name" -> brand.name,
      "logo" -> brand.logo,
      "url" -> brand.url,
      "sites" -> brand.sites,
      "siteAttributes" -> brand.siteAttributes

    )
  }

  implicit object BrandReader extends BSONDocumentReader[Brand] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): Brand = Brand(
      doc.getAs[String]("_id"),
      doc.getAs[String]("name"),
      doc.getAs[String]("logo"),
      doc.getAs[String]("url"),
      doc.getAs[Seq[String]]("sites"),
      doc.getAs[Map[String, Map[String, String]]]("siteAttributes")
    )
  }

  def fromDocument(doc : SolrDocument) : Brand = {
    def attribute(name: String) = Option(doc.get(name).asInstanceOf[String])
    def seqAttribute(name: String) = Option(doc.get(name).asInstanceOf[Seq[String]])

    val id = attribute("id")
    val name = attribute("name")
    val logo = attribute("logo")
    val url = attribute("url")
    val sites = seqAttribute("siteId")

    new Brand(id, name, logo, url, sites)
  }
}

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
   var id: Option[String] = None,
   var name: Option[String] = None,
   var logo: Option[String] = None,
   var url: Option[String] = None,
   var sites: Option[Seq[String]] = None,
   var siteAttributes: Option[Map[String, Map[String, String]]] = None) extends IndexableElement {

  def getId : String = this.id.orNull
  
  def getName : String = this.name.orNull

  def getUrl: String = url.orNull

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
    name.orNull
  }

  def getType : String = {
    "brand"
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



