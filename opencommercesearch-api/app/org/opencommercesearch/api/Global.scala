package org.opencommercesearch.api

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import play.api.{Play, Logger, Application}
import play.api.libs.json.Json

import org.apache.solr.client.solrj.AsyncSolrServer
import org.apache.solr.client.solrj.impl.AsyncCloudSolrServer
import play.api.mvc.{Result, WithFilters, RequestHeader}
import play.api.mvc.Results._
import play.modules.statsd.api.StatsdFilter
import scala.concurrent.Future
import org.opencommercesearch.api.service.{StorageFactory, MongoStorageFactory}


object Global extends WithFilters(new StatsdFilter()) {
  lazy val RealTimeRequestHandler = getConfigString("realtimeRequestHandler", "/get")
  lazy val MaxUpdateBrandBatchSize = getConfigInt("brand.maxUpdateBatchSize", 100)
  lazy val MaxUpdateProductBatchSize = getConfigInt("product.maxUpdateBatchSize", 100)
  lazy val MaxUpdateCategoryBatchSize = getConfigInt("category.maxUpdateBatchSize", 100)
  lazy val MaxUpdateRuleBatchSize = getConfigInt("rule.maxUpdateBatchSize", 100)
  lazy val MaxUpdateFacetBatchSize = getConfigInt("facet.maxUpdateBatchSize", 100)
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
  lazy val MinSuggestQuerySize = getConfigInt("minSuggestQuerySize", 2)

  /**
   * Rule preview collection from configuration.
   */
  lazy val RulePreviewCollection = getConfigString("preview.ruleCollection", "rulePreview")

  /**
   * * Rule public collection from configuration.
   */
  lazy val RulePublicCollection = getConfigString("public.ruleCollection", "rulePublic")

  /**
   * Facet preview collection from configuration.
   */
  lazy val FacetPreviewCollection = getConfigString("preview.facetCollection", "facetsPreview")

  /**
   * * Facet public collection from configuration.
   */
  lazy val FacetPublicCollection = getConfigString("public.facetCollection", "facetsPublic")

  // @todo evaluate using dependency injection, for the moment lets be pragmatic
  private var _solrServer: AsyncSolrServer = null
  private var _storageFactory: MongoStorageFactory = null

  def solrServer = {
    if (_solrServer == null) {
      _solrServer = AsyncCloudSolrServer(getConfigString("zkHost", "localhost:2181"))
    }
    _solrServer
  }

  def solrServer_=(server: AsyncSolrServer) = { _solrServer = server }


  def storageFactory =  {
    if (_storageFactory == null) {
      _storageFactory = new MongoStorageFactory
      _storageFactory.setConfig(Play.current.configuration)
      _storageFactory.setClassLoader(Play.current.classloader)
    }
    _storageFactory
  }

  def storageFactory_=(storageFactory: MongoStorageFactory) = { _storageFactory = storageFactory }

  override def onStart(app: Application) {
    Logger.info("OpenCommerceSearch API has started")
  }

  override def onStop(app: Application) {
    Logger.info("OpenCommerceSearch API shutdown...")
    storageFactory.close
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    Future.successful(ex.getCause match {
   	  case e:IllegalArgumentException => BadRequest(e.getMessage)
   	  case other => {
        Logger.error("Unexpected error",  other)
        InternalServerError(other.getMessage)
      }
   	})
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(Json.obj(
      "message" -> "Resource not found")))
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest(Json.obj(
      "message" -> error
    )))
  }

  def getConfigString(name: String, default: String) = {
    Play.current.configuration.getString(name).getOrElse(default)
  }

  def getConfigInt(name: String, default: Int) = {
    Play.current.configuration.getInt(name).getOrElse(default)
  }
}
