package org.opencommercesearch.api.models.debug

import play.api.libs.json.Json

case class DebugInfo(
                      var rulesDebug: Option[RulesDebug] = None,
                      var solrDebug: Option[QueryExplain] = None) {

  def setRulesDebug(rulesDebug: RulesDebug): Unit = {
    this.rulesDebug = Option(rulesDebug)
  }

  def setSolrDebug(solrDebug: QueryExplain): Unit = {
    this.solrDebug = Option(solrDebug)
  }
}

object DebugInfo {
  implicit val readsRule = Json.reads[DebugInfo]
  implicit val writesRule = Json.writes[DebugInfo]
}



