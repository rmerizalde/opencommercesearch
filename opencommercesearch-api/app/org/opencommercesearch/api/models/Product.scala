package org.opencommercesearch.api.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.util

import org.apache.solr.client.solrj.beans.Field

case class Product (var id: Option[String], var title: Option[String], var skus: Option[Seq[Sku]]) {
  def this() = this(null, null, null)

  @Field
  def setId(id: String) : Unit = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) : Unit = { this.title = Option.apply(title) }

  @Field
  def setSkus(skus: Seq[Sku]) : Unit = { this.skus = Option.apply(skus) }
}

object Product {

  implicit val readsProduct = Json.reads[Product]
  implicit val writesProduct = Json.writes[Product]

  implicit val objectSeqFormat = new Format[Seq[Sku]] {
    def writes(seq: Seq[Sku]): JsValue = {
      Json.arr(
        seq.map(s => Json.toJson(s))
      )
    }

    def reads(jv: JsValue): JsResult[Seq[Sku]] =
      JsSuccess(jv.as[JsArray].value.map(_.as[Sku]))
  }

}
