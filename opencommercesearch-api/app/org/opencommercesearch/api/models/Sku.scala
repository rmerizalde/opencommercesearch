package org.opencommercesearch.api.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

import org.apache.solr.client.solrj.beans.Field

case class Sku (
  var id: Option[String],
  var title: Option[String],
  var image: Option[String],
  var isToos: Option[Boolean],
  var isPastSeason: Option[Boolean],
  var isCloseout: Option[Boolean],
  var reviews: Option[Int],
  var bayesianReviewAverage: Option[Float],
// @TODO implement a better way to handle site specific properties
  var hasFreeGiftbcs: Option[Boolean]) {

  def this() = this(None, None, None, None, None, None, None, None, None)


  @Field
  def setId(id: String) = { this.id = Option.apply(id) }

  @Field
  def setTitle(title: String) = { this.title = Option.apply(title) }

  @Field
  def setImage(image: String) = { this.image = Option.apply(image) }

  @Field("isToos")
  def setToos(isToos: Boolean) = { this.isToos = Option.apply(isToos) }

  @Field("isPastSeason")
  def setPastSeason(isPastSeason: Boolean) = { this.isPastSeason = Option.apply(isPastSeason) }

  @Field ("isCloseout")
  def setCloseout(isCloseout: Boolean) = { this.isCloseout = Option.apply(isCloseout) }

  @Field
  def setReviews(reviews: Int) = { this.reviews = Option.apply(reviews) }

  @Field
  def setBayesianReviewAverage(bayesianReviewAverage: Float) = { this.bayesianReviewAverage = Option.apply(bayesianReviewAverage) }

  @Field("freeGiftbcs")
  def setFreeGiftbcs(hasFreeGiftbcs: Boolean) = { this.hasFreeGiftbcs = Option.apply(hasFreeGiftbcs) }
}

object Sku {


  implicit val readsSku = Json.reads[Sku]
  implicit val writesSku = Json.writes[Sku]


}


