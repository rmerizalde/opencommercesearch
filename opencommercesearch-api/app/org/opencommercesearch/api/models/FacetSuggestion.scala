
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

import java.sql.Timestamp
import java.util
import java.util.Date

import org.apache.commons.lang3.StringUtils
import org.apache.solr.common.SolrInputDocument
import org.opencommercesearch.api.Global.{IndexOemProductsEnabled, ProductAvailabilityStatusSummary}
import org.opencommercesearch.api.Implicits._
import org.opencommercesearch.api.models.ProductList._
import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context
import org.opencommercesearch.search.Element
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

object FacetSuggestion {

  implicit val readsFacetSuggestion : Reads[FacetSuggestion] = new Reads[FacetSuggestion] {  

      def reads(json: JsValue): JsResult[FacetSuggestion] = {
        JsSuccess(FacetSuggestion(
          (json \ "id").asOpt[String],
          (json \ "facet").asOpt[Facet]))
      }
    }

  implicit val writesFacetSuggestion : Writes[FacetSuggestion] = new Writes[FacetSuggestion] {

      def writes(product: FacetSuggestion) = Json.obj("id" -> product.id.get, "facet" -> product.facet.get)

  }

}

case class FacetSuggestion (
  var id: Option[String] = None,
  var facet: Option[Facet] = None) extends Element
{
  def getId : String = this.id.get

  override def source = "facetSuggestion"

  override def toJson : JsValue = Json.toJson(this)
  
}
