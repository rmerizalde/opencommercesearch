package org.opencommercesearch.api.models.debug

import java.util

import org.apache.solr.common.util.NamedList
import play.api.libs.json.Json
import scala.collection.JavaConversions._
import scala.collection.mutable

case class QueryExplain(
                       var querystring: Option[String] = None,
                       var parsedquery: Option[String] = None,
                       var parsedquery_toString: Option[String] = None,
                       var expandedSynonyms: Option[Seq[String]] = None,
                       var reasonForNotExpandingSynonyms: Option[Seq[String]] = None,
                       var explain: Option[Map[String,ScoreNode]] = None){

  def convertJavaResponse(debugMap: NamedList[AnyRef]) : Unit = {
    debugMap map { mapEntry =>
      if ("querystring".equals(mapEntry.getKey)) {
        querystring = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("parsedquery".equals(mapEntry.getKey)) {
        parsedquery = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("parsedquery_toString".equals(mapEntry.getKey)) {
        parsedquery_toString = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("expandedSynonyms".equals(mapEntry.getKey)) {
        val scalaList = new mutable.ArrayBuffer[String]
        mapEntry.getValue.asInstanceOf[util.List[String]].foreach(element => scalaList += element)
        expandedSynonyms = Option(scalaList)
      } else if ("reasonForNotExpandingSynonyms".equals(mapEntry.getKey)) {
        val scalaList = new mutable.ArrayBuffer[String]
        mapEntry.getValue.asInstanceOf[NamedList[String]].foreach(element => scalaList += element.getKey += element.getValue)
        reasonForNotExpandingSynonyms = Option(scalaList)
      } else if ("explain".equals(mapEntry.getKey)) {
        val scalaExplainMap = new mutable.LinkedHashMap[String,ScoreNode]
        mapEntry.getValue.asInstanceOf[NamedList[AnyRef]].map {
          element => {
            val scoreNode = new ScoreNode()
            scoreNode.convertJavaResponse(element.getValue.asInstanceOf[NamedList[AnyRef]])
            scalaExplainMap += (element.getKey -> scoreNode)
          }
        }
        //TODO gsegura: here when I do scalaExplainMap.toMap I loose the order of the linkedHashMap. I can't change the type
        //cause I get a Json.[reads|writes] error. Need to figure out a good solution to preserve the order of the response
        explain = Option(scalaExplainMap.toMap)
      }
    }
  }
}

object QueryExplain {

  implicit val readsRule = Json.reads[QueryExplain]
  implicit val writesRule = Json.writes[QueryExplain]
}
