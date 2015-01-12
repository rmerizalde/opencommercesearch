package org.opencommercesearch.api.models.debug

import play.api.libs.json.Json

case class Synonyms(var expanded: Option[Seq[String]] = None,
                    var explain: Option[String] = None) {

}

object Synonyms {
  implicit val readsRule = Json.reads[Synonyms]
  implicit val writesRule = Json.writes[Synonyms]
}
