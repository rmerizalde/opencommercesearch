package org.opencommercesearch.api

import play.api.Logger
import play.api.mvc._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object AccessLog extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[SimpleResult])(request: RequestHeader): Future[SimpleResult] = {
    nextFilter(request).map { result =>
      val msg = s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress} " +
        s"domain=${request.domain} query-string=${request.rawQueryString} " +
        s"referer=${request.headers.get("referer").getOrElse("N/A")} " +
        s"user-agent=[${request.headers.get("user-agent").getOrElse("N/A")}]"
      play.Logger.of("accesslog").info(msg)
      result
    }
  }
}