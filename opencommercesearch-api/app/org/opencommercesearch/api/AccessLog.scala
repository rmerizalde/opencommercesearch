package org.opencommercesearch.api

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future

object AccessLog extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    nextFilter(request).map { result =>
      val msg = s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress} " +
        s"domain=${request.domain} query-string=${request.rawQueryString} " +
        s"referer=${request.headers.get("referer").getOrElse("N/A")} " +
        s"user-agent=[${request.headers.get("user-agent").getOrElse("N/A")}] " +
        s"trace=${request.headers.get("x-bc-trace-id").getOrElse("N/A")}"
      play.Logger.of("accesslog").info(msg)
      result
    }
  }
}