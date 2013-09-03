package org.opencommercesearch.api

import play.api.{Play, GlobalSettings, Logger, Application}
import play.api.libs.json.Json

import org.apache.solr.client.solrj.impl.AsyncCloudSolrServer
import play.api.mvc.{Result, WithFilters, RequestHeader}
import play.api.mvc.Results._
import play.modules.statsd.api.StatsdFilter

object Global extends WithFilters(new StatsdFilter()) {
  lazy val RealTimeRequestHandler = getConfigString("realtimeRequestHandler", "/get")
  lazy val MaxUpdateBatchSize = getConfigInt("maxUpdateBatchSize", 100)
  lazy val BrandPreviewCollection = getConfigString("preview.brandCollection", "brandsPreview")
  lazy val BrandPublicCollection = getConfigString("public.brandCollection", "brandsPublic")
  lazy val ProductPreviewCollection = getConfigString("preview.productionCollection", "catalogPreview")
  lazy val ProductPublicCollection = getConfigString("public.productionCollection", "catalogPublic")
  lazy val MaxPaginationLimit = getConfigInt("maxPaginationLimit", 20)

  // @todo evaluate using dependency injection, for the moment lets be pragmatic
  private var _solrServer: AsyncCloudSolrServer = null

  def solrServer = {
    if (_solrServer == null) {
      _solrServer = AsyncCloudSolrServer(getConfigString("zkHost", "localhost:2181"))
    }
    _solrServer
  }

  def solrServer_=(server: AsyncCloudSolrServer) = { _solrServer = server }


  override def onStart(app: Application) {
    Logger.info("OpenCommerceSearch API has started")
  }

  override def onStop(app: Application) {
    Logger.info("OpenCommerceSearch API shutdown...")
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    ex.getCause match {
   	  case e:IllegalArgumentException => BadRequest(e.getMessage)
   	  case other => {
        Logger.error("Unexpected error",  other)
        InternalServerError(other.getMessage)
      }
   	}
  }

  override def onHandlerNotFound(request: RequestHeader): Result = {
    NotFound(Json.obj(
      "message" -> "Resource not found"))
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    BadRequest(Json.obj(
      "message" -> error
    ))
  }

  def getConfigString(name: String, default: String) = {
    Play.current.configuration.getString(name).getOrElse(default)
  }

  def getConfigInt(name: String, default: Int) = {
    Play.current.configuration.getInt(name).getOrElse(default)
  }
}
