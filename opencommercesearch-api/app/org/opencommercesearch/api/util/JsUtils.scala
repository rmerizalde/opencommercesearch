package org.opencommercesearch.api.util

import play.api.libs.json._

/**
 * Utility class for Json parsing, serialization, de-serialization extensions
 * @author jmendez
 */
object JsUtils {

  /**
   * Implicit class that adds extra functionality for handling Json paths (on serialization (writes), de-serialization(reads))
   * @param path JsPath in the current context
   */
  implicit class PathAdditions(path: JsPath) {

    /**
     * Writes an Option[T] at JsPath using the explicit Writes[T] passed by name which is useful in case of
     * recursive case classes for example. This method differs from regular lazyWriteNullable in that it receives
     * Iterable subclasses and in addition of ignoring null values, it doesn't write anything if the Iterable
     * has no items. This prevents empty Json objects to be serialized.
     *
     * {{{
     * case class User(id: Long, name: String, friend: Option[Seq[User]])
     *
     * implicit lazy val UserWrites: Writes[User] = (
     *   (__ \ 'id).write[Long] and
     *   (__ \ 'name).write[String] and
     *   (__ \ 'friend).lazyWriteNullableIterable[Seq[User]](Writes.traversableWrites[User](UserWrites)
     * )(User.apply _)
     * }}}
     */
    def lazyWriteNullableIterable[T <: Iterable[_]](w: => Writes[T]): OWrites[Option[T]] = OWrites((t: Option[T]) => {
      if(t != null) {
        t.getOrElse(Seq.empty).size match {
          case 0 => Json.obj()
          case _ => Writes.nullable[T](path)(w).writes(t)
        }
      }
      else {
        Json.obj()
      }
    })
  }
}