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

import java.math.MathContext

import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.{BSONDocumentReader, BSONDocument, BSONDocumentWriter}

/**
 * This class represents a quantity of any measurement. Measurement unit can be converted if a conversion ratio exists.
 *
 * @todo add compound units, only atomic units are supported
 * @todo add more conversions and supporting conversion combinations if a direct conversion does not exist
 *       For now, the idea is to represent measurements in way that can easily be extended in the future if i13n features
 *       are needed.
 * @todo do something similar with money fields
 *
 * @author rmerizalde
 */
case class Quantity(amount: BigDecimal, unit: MeasurementUnit) {

  def to(toUnit: MeasurementUnit): Quantity = {
    val conversionRatio = unit.conversionRatios().find(c => c.multiplier(unit, toUnit).isDefined)

    conversionRatio match {
      case Some(ratio) =>
        val newAmount = amount * ratio.multiplier(unit, toUnit).get

        Quantity(newAmount.bigDecimal.stripTrailingZeros(), toUnit)
      case _ => throw new UnsupportedConversionRatio(unit, toUnit)
    }
  }
}

object Quantity {
  val mathContext = new MathContext(8)

  def applyInternal(amount: BigDecimal, unit: String) = Quantity(amount, MeasurementUnit(unit))

  def unapplyInternal(quantity: Quantity): Option[(BigDecimal, String)] = if (quantity != null) Some(quantity.amount, quantity.unit.symbol) else None

  implicit val readsQuantity: Reads[Quantity] = (
      (__ \ "amount").read[BigDecimal] ~
      (__ \ "unit").read[String]
    ) (Quantity.applyInternal _)

  implicit val writesQuantity: Writes[Quantity] = (
      (__ \ "amount").write[BigDecimal] ~
      (__ \ "unit").write[String]
    ) (unlift(Quantity.unapplyInternal))

  implicit object QuantityWriter extends BSONDocumentWriter[Quantity] {
    import org.opencommercesearch.bson.BSONFormats._

    def write(quantity: Quantity): BSONDocument = BSONDocument(
      "amount" -> quantity.amount,
      "unit" -> quantity.unit.symbol
    )
  }

  implicit object SkuReader extends BSONDocumentReader[Quantity] {
    import org.opencommercesearch.bson.BSONFormats._

    def read(doc: BSONDocument): Quantity = Quantity(
      amount = doc.getAs[BigDecimal]("amount").get,
      unit =  MeasurementUnit(doc.getAs[String]("unit").get)
    )
  }
}


abstract sealed class MeasurementUnit(val symbol: String) {
  def conversionRatios(): Seq[ConversionRatio]
}

object MeasurementUnit {
  def apply(symbol: String) = symbol match {
    case "g" => Gram
    case "lb" => Pound
    case _ => throw new UnsupportedMeasurementUnit(symbol)
  }
}

case object Gram extends MeasurementUnit("g") {
  def conversionRatios() = Seq(Gram2Pound)
}

case object Pound extends MeasurementUnit("lb") {
  def conversionRatios() = Seq(Gram2Pound)
}

abstract sealed class ConversionRatio(val ratio: BigDecimal) {
  val inverseRatio = 1 / ratio

  def multiplier(from: MeasurementUnit, to: MeasurementUnit): Option[BigDecimal]
}

case object Gram2Pound extends ConversionRatio(BigDecimal("0.00220462")) {
  def multiplier(from: MeasurementUnit, to: MeasurementUnit): Option[BigDecimal] = {
    (from, to) match {
      case (Gram, Pound) => Some(ratio)
      case (Pound, Gram) => Some(inverseRatio)
      case _ => None
    }
  }
}

class UnsupportedMeasurementUnit(symbol: String) extends Exception(symbol)

class UnsupportedConversionRatio(from: MeasurementUnit, to: MeasurementUnit) extends Exception(to.symbol + " to " + from.symbol)
