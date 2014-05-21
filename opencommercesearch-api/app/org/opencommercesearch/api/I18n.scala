package org.opencommercesearch.api

import play.api.mvc.Request
import play.api.i18n.Lang

/**
 * Some convenient i18n definitions
 * @author rmerizalde
 */
object I18n {
  val DefaultLang = Lang("en", "US")
  val SupportedLocales = Seq(DefaultLang, Lang("en", "CA"), Lang("fr", "CA"), Lang("en"), Lang("fr"))

  def language()(implicit request: Request[_]) = request.acceptLanguages.collectFirst({ case l if SupportedLocales.contains(l) => l}).getOrElse(DefaultLang)
}
