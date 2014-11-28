package org.opencommercesearch.api.models.debug

import java.util

import org.apache.solr.common.util.NamedList
import play.api.libs.json.Json
import scala.collection.JavaConversions._
import scala.collection.mutable

case class DebugInfo(
                      var params: Option[String] = None,
                      var rules: Option[Map[String, Seq[Map[String, String]]]] = None,
                      var synonyms: Option[Synonyms]= None,
                      var query: Option[Query]= None) {

  def processRulesResponse(rulesDebugMap: util.Map[String, AnyRef]) : Unit = {
    rulesDebugMap map { entry =>
      if ("ruleParams".equals(entry._1)) {
        params = Option(entry._2.asInstanceOf[String])
      } else if ("rules".equals(entry._1)) {
        val rulesMap = entry._2.asInstanceOf[util.HashMap[String, util.List[ util.Map[String, String]]]].toMap

        val scalaRulesMap = rulesMap.map {
          case (key,value) => {
            val scalaRulesList = new mutable.ArrayBuffer[Map[String, String]]
            value.foreach(entry => {
              scalaRulesList += entry.toMap
            })
            key -> scalaRulesList.toSeq
          }
        }
        this.rules = Option(scalaRulesMap)
      }
    }
  }

  def processSolrResponse(debugMap: NamedList[AnyRef]) : Unit = {
    val query = new Query()
    val synonyms = new Synonyms()

    debugMap map { mapEntry =>
      if ("querystring".equals(mapEntry.getKey)) {
        query.queryString = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("parsedquery".equals(mapEntry.getKey)) {
        query.parsedQuery = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("parsedquery_toString".equals(mapEntry.getKey)) {
        query.finalQuery = Option(mapEntry.getValue.asInstanceOf[String])
      } else if ("expandedSynonyms".equals(mapEntry.getKey)) {
        val scalaList = new mutable.ArrayBuffer[String]
        mapEntry.getValue.asInstanceOf[util.List[String]].foreach(element => scalaList += element)
        if(!scalaList.isEmpty) {
          synonyms.expanded = Option(scalaList)
        }
      } else if ("reasonForNotExpandingSynonyms".equals(mapEntry.getKey)) {
        mapEntry.getValue.asInstanceOf[NamedList[String]].foreach(element => {
          if(element.getKey == "explanation") {
            synonyms.explain = Option(element.getValue)
          }
        })
      } else if ("explain".equals(mapEntry.getKey)) {
        val scalaExplainMap = new mutable.LinkedHashMap[String,QueryScore]
        mapEntry.getValue.asInstanceOf[NamedList[AnyRef]].map {
          element => {
            val scoreNode = new QueryScore()
            scoreNode.convertJavaResponse(element.getValue.asInstanceOf[NamedList[AnyRef]])
            scalaExplainMap += (element.getKey -> scoreNode)
          }
        }
        //TODO gsegura: here when I do scalaExplainMap.toMap I loose the order of the linkedHashMap. I can't change the type
        //cause I get a Json.[reads|writes] error. Need to figure out a good solution to preserve the order of the response
        query.explain = Option(scalaExplainMap.toMap)
      }
    }

    this.query = Option(query)
    if (synonyms.expanded.isDefined || synonyms.explain.isDefined) {
      this.synonyms = Option(synonyms)
    }
  }
}

object DebugInfo {
  implicit val readsRule = Json.reads[DebugInfo]
  implicit val writesRule = Json.writes[DebugInfo]
}



