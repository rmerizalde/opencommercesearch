package org.opencommercesearch.api.models.debug

import play.api.libs.json.Json
import java.util

import scala.collection.mutable
import scala.collection.JavaConversions._

case class RulesDebug(
                       var ruleParams: Option[String] = None,
                       var rules: Option[Map[String, Seq[Map[String, String]]]] = None) {

  def setRuleParams(ruleParams: String) : Unit = {
    this.ruleParams = Option(ruleParams)
  }
  def setRules(rules: Map[String, Seq[Map[String, String]]]) : Unit = {
    this.rules = Option(rules)
  }

  def convertJavaResponse(rulesDebugMap: util.Map[String, AnyRef]) : Unit = {
    rulesDebugMap map { entry =>
      if ("ruleParams".equals(entry._1)) {
        ruleParams = Option(entry._2.asInstanceOf[String])
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
}

object RulesDebug {
  implicit val readsRule = Json.reads[RulesDebug]
  implicit val writesRule = Json.writes[RulesDebug]
}