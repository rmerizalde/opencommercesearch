package org.opencommercesearch.api

import play.api.mvc.Request
import play.api.mvc.Call

/**
 * @todo figure out if there a way to use Call.absoluteUrl with play.api.mvc.Request
 */
object Util {

  /**
   * Transform the given call to an absolute URL.
   */
  def absoluteURL[T](call: Call, request: Request[T], secure: Boolean = false) : String = {
    s"http${if (secure) "s" else ""}://${request.host}${call.url}"
  }
}
