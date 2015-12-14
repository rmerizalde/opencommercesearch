package org.opencommercesearch.api.controllers

import org.opencommercesearch.api.Global
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.modules.statsd.api.Statsd

trait Retrying {

  val RetryKey = "api.search.solr.retry"
  val FailedKey = "api.search.solr.failed"

  /**
   * Given an operation that produces a Future[T], returns a Future containing the result of T, unless an exception is thrown,
   * in which case the operation will be retried, if there are more possible retries, which is configured through
   * the 'solr.error.retry' parameter.
   * If the operation does not succeed and there is no retries left, the resulting Future will contain the last failure.
   **/
  def retry[T](f: => Future[T]): Future[T] = {
    retry(f, Global.SolrErrorRetry)
  }

  def retry[T](f: => Future[T], retries: Int): Future[T] = {
    f recoverWith {
      case e if retries > 0 => {
        Logger.warn("Retrying request", e)
        Statsd.increment(RetryKey)
        retry(f, retries - 1)
      }
      case e if retries <= 0 => {
        Logger.error("Retries exceeded", e)
        Statsd.increment(FailedKey)
        throw e
      }
    }
  }
}
