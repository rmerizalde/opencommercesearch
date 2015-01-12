package org.opencommercesearch.api.models.debug

import java.util

import org.apache.solr.common.util.NamedList
import play.api.libs.json.Json
import scala.collection.JavaConversions._
import scala.collection.mutable

case class Query(
                       var queryString: Option[String] = None,
                       var parsedQuery: Option[String] = None,
                       var finalQuery: Option[String] = None,
                       var explain: Option[Map[String,QueryScore]] = None){

}

object Query {

  implicit val readsRule = Json.reads[Query]
  implicit val writesRule = Json.writes[Query]
}
