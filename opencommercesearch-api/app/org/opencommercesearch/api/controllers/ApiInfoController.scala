package org.opencommercesearch.api.controllers

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

import play.api.mvc._
import play.api.libs.json.{JsArray, Json}
import org.opencommercesearch.api.Global._
import org.apache.solr.client.solrj.impl.{AsyncCloudSolrServer, AsyncHttpSolrServer}
import org.apache.solr.common.cloud.ClusterState
import scala.collection.JavaConverters._
import org.noggit.{CharArr, JSONWriter}

object ApiInfoController extends BaseController {
  /**
   * Utility method that shows the current client state
   */
  def getInfo = Action { request =>
    var responseBody = Json.obj("Server class" -> solrServer.getClass.toString)

    solrServer match {
      case server: AsyncHttpSolrServer =>
        responseBody = responseBody++ Json.obj("Base URL" -> server.baseUrl)
      case server: AsyncCloudSolrServer =>
        responseBody = responseBody ++ Json.obj(
          "Cached URLs" -> server.urlLists.toString,
          "Cached leader URLs" -> server.leaderUrlLists.toString,
          "Cached replica URLs" -> server.replicasLists.toString,
          "Zookeeper timeout" -> server.zkConnectTimeout,
          "Zookeeper client timeout" -> server.zkClientTimeout)

        if(server.zkStateReader != null && server.zkStateReader.getClusterState != null) {
          responseBody = responseBody ++ Json.obj(
            "Zookeeper live nodes" -> server.zkStateReader.getClusterState.getLiveNodes.toString,
            "Zookeeper collections" -> getCollectionStates(server.zkStateReader.getClusterState))
        }
      case _ => throw new ClassCastException
    }

    Ok (Json.obj("status" -> responseBody))
  }

  /**
   * Converts the list of collection states from Zookeeper into a printable JsArray
   * @param clusterState Zookeeper cluster state
   * @return Printable JsArray with the information of Zookeeper collection states
   */
  private def getCollectionStates(clusterState : ClusterState) : JsArray = {
    var array = new JsArray()
    val collectionStates = clusterState.getCollectionStates.asScala.mapValues(docCollection => {
      val out = new CharArr
      val jsonWriter = new JSONWriter(out)
      docCollection.write(jsonWriter)
      Json.parse(out.toString)
    })

    collectionStates.foreach(state =>
      array = array.append(Json.obj(state._1 -> state._2))
    )

    array
  }
}
