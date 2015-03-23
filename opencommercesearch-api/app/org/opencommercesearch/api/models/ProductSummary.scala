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

/**
 * Provides functionality to summarize field values. Supports the same functions as the group summary component in the
 * search engine
 */
sealed case class FieldSummary[T] (
  values: Option[Seq[T]],
  var min: Option[T] = None,
  var max: Option[T] = None,
  var count: Option[Int] = None,
  var distinct: Option[Set[T]] = None,
  var buckets: Option[Map[String, Int]] = None)(implicit ordering: Ordering[T]) {

  def summarize(functions: Seq[String]) = functions.foreach {
    case "min" => Min()
    case "max" => Max()
    case "count" => Count()
    case "bucket" => Bucket()
    case "distinct" => Distinct()
    case _ =>
  }

  private val Min = () => min = values.map(_.min(ordering))
  private val Max = () => max = values.map(_.max(ordering))
  private val Count = () => count = values.map(_.toSet.size)
  private val Bucket = () => buckets = values.map(_.map(_.toString).foldLeft(Map.empty[String, Int].withDefaultValue(0))(bucket))
  private val Distinct = () => distinct = values.map(_.toSet)

  private def bucket(bucketMap: Map[String, Int], value: String): Map[String, Int] = {
    val count: Int = bucketMap(value) + 1
    bucketMap + (value -> count)
  }
}

object FieldSummary {
  implicit def FieldSummaryWrites[T](implicit fmt: Writes[T]): Writes[FieldSummary[T]] = new Writes[FieldSummary[T]] {
    def writes(fs: FieldSummary[T]): JsValue = {
      val fields = Seq(
        fs.min.map(m => ("min", fmt.writes(m))),
        fs.max.map(m => ("max", fmt.writes(m))),
        fs.count.map(c => ("count", Json.toJson(c))),
        fs.distinct.map(d => ("distinct", Json.toJson(d))),
        fs.buckets.map(b => ("buckets", Json.toJson(b))))

      new JsObject(fields.flatten)
    }
  }
}

case class ProductSummary(
  var listPrice: Option[FieldSummary[BigDecimal]] = None,
  var salePrice: Option[FieldSummary[BigDecimal]] = None,
  var discountPercent: Option[FieldSummary[Int]] = None,
  var color: Option[FieldSummary[String]] = None,
  var colorFamily: Option[FieldSummary[String]] = None,
  var isRetail: Option[FieldSummary[Boolean]] = None,
  var isOutlet: Option[FieldSummary[Boolean]] = None,
  var onSale: Option[FieldSummary[Boolean]] = None) {
}

object ProductSummary {
  implicit val writesProductSummary = Json.writes[ProductSummary]
  val MinMax = Seq("min", "max")
  val Count = Seq("count")
  val Distinct = Seq("distinct")
  val Bucket = Seq("bucket")

  def summarize(product: Product): Option[ProductSummary] = {
    def fieldSummary[T](values: Seq[Option[T]], functions: Seq[String])(implicit ordering: Ordering[T]) = {
      values.flatten match {
        case Seq() => None
        case v =>
          val fs = FieldSummary[T](Some(v))
          fs.summarize(functions)
          Some(fs)
      }
    }

    product.skus.map { skus =>
      skus.map(sku => (sku.listPrice, sku.salePrice, sku.discountPercent, sku.color.map(_.name).flatten, sku.color.map(_.family).flatten, sku.isRetail, sku.isOutlet, sku.onSale))
    } match {
      case Some(tuples) =>
        Some(new ProductSummary(
          fieldSummary(tuples.map(_._1), MinMax),
          fieldSummary(tuples.map(_._2), MinMax),
          fieldSummary(tuples.map(_._3), MinMax),
          fieldSummary(tuples.map(_._4), Count),
          fieldSummary(tuples.map(_._5), Distinct),
          fieldSummary(tuples.map(_._6), Bucket),
          fieldSummary(tuples.map(_._7), Bucket),
          fieldSummary(tuples.map(_._8), Bucket)))
      case None => None
    }

  }
}
