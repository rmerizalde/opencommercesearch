package org.opencommercesearch.api

import play.api.{Play, GlobalSettings, Logger, Application}
import play.api.libs.json.Json

import org.apache.solr.client.solrj.AsyncSolrServer
import org.apache.solr.client.solrj.impl.AsyncCloudSolrServer
import play.api.mvc.{Result, WithFilters, RequestHeader}
import play.api.mvc.Results._
import play.modules.statsd.api.StatsdFilter


object Global extends WithFilters(new StatsdFilter()) {
  lazy val RealTimeRequestHandler = getConfigString("realtimeRequestHandler", "/get")
  lazy val MaxUpdateBrandBatchSize = getConfigInt("brand.maxUpdateBatchSize", 100)
  lazy val MaxUpdateProductBatchSize = getConfigInt("brand.maxUpdateBatchSize", 100)
  lazy val MaxUpdateCategoryBatchSize = getConfigInt("category.maxUpdateBatchSize", 100)
  lazy val BrandPreviewCollection = getConfigString("preview.brandCollection", "brandsPreview")
  lazy val BrandPublicCollection = getConfigString("public.brandCollection", "brandsPublic")
  lazy val SearchPreviewCollection = getConfigString("preview.searchCollection", "catalogPreview")
  lazy val SearchPublicCollection = getConfigString("public.searchCollection", "catalogPublic")
  lazy val ProductPreviewCollection = getConfigString("preview.productCollection", "productsPreview")
  lazy val ProductPublicCollection = getConfigString("public.productCollection", "productsPublic")
  lazy val CategoryPreviewCollection = getConfigString("preview.categoryCollection", "categoriesPreview")
  lazy val CategoryPublicCollection = getConfigString("public.categoryCollection", "categoriesPublic")
  lazy val QueryCollection = getConfigString("public.queryCollection", "autocomplete")
  lazy val CategoryCacheTtl = getConfigInt("category.cache.ttl", 60 * 10)
  lazy val MaxPaginationLimit = getConfigInt("maxPaginationLimit", 40)
  lazy val DefaultPaginationLimit = getConfigInt("maxPaginationLimit", 10)

  // @todo evaluate using dependency injection, for the moment lets be pragmatic
  private var _solrServer: AsyncSolrServer = null

  def solrServer = {
    if (_solrServer == null) {
      _solrServer = AsyncCloudSolrServer(getConfigString("zkHost", "localhost:2181"))
    }
    _solrServer
  }

  def solrServer_=(server: AsyncSolrServer) = { _solrServer = server }

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
