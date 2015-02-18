package org.opencommercesearch.bson

import java.util.Date
import reactivemongo.bson._

/**
 * Some implicit conversions
 *
 * @author rmerizalde
 */
object BSONFormats {

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, Date] {
    def read(date: BSONDateTime) = new Date(date.value)
    def write(date: Date) = BSONDateTime(date.getTime)
  }

  implicit object BSONBigDecimalHandler extends BSONHandler[BSONString, BigDecimal] {
    def read(str: BSONString) = BigDecimal(str.value)
    def write(bd: BigDecimal) = BSONString(bd.toString())
  }

  implicit def MapBSONReader[T](implicit reader: BSONReader[_ <: BSONValue, T]): BSONDocumentReader[Map[String, T]] =
    new BSONDocumentReader[Map[String, T]] {
      def read(doc: BSONDocument): Map[String, T] = {
        doc.elements.collect {
          case (key, value) => value.seeAsOpt[T](reader) map {
            ov => (key, ov)
          }
        }.flatten.toMap
      }
    }

  implicit def MapBSONWriter[T](implicit writer: BSONWriter[T, _ <: BSONValue]): BSONDocumentWriter[Map[String, T]] = new BSONDocumentWriter[Map[String, T]] {
    def write(doc: Map[String, T]): BSONDocument = {
      BSONDocument(doc.toTraversable map (t => (t._1, writer.write(t._2))))
    }
  }
}
