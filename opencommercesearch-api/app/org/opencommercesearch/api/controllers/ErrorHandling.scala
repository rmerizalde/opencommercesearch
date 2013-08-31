package org.opencommercesearch.api.controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Controller, Result}
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.Future

trait ErrorHandling {
  self: Controller =>

  def withErrorHandling(f: Future[Result], message: String) : Future[Result]  = {
    f.recover { case t: Throwable =>
      Logger.error(message, t)
      InternalServerError(Json.obj(
        // @Todo refine developer messages ??
        "message" -> message))
    }
  }
}
