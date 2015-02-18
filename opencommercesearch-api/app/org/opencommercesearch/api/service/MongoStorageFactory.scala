package org.opencommercesearch.api.service

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

import java.util.concurrent.ConcurrentHashMap

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Configuration, Logger}
import reactivemongo.api.{MongoConnection, MongoDriver}
import reactivemongo.core.commands.LastError

import scala.util.{Failure, Success}

/**
 * MongoDB storage factory implementation
 *
 * @author rmerizalde
 */
class MongoStorageFactory extends StorageFactory[LastError] {

  val storages = new ConcurrentHashMap[String, MongoStorage]()
  var driver: MongoDriver = null
  var connection: MongoConnection = null
  var config: Configuration = null
  var classLoader: ClassLoader = null

  def setConfig(config: Configuration) = {
    this.config = config
  }

  def setClassLoader(classLoader: ClassLoader) = {
    this.classLoader = classLoader
  }

  /**
   * Returns the storage for the given namespace
   *
   * @param namespace is the database name
   * @return the storage instance for the given namespace
   */
  def getInstance(namespace: String) : MongoStorage = {
    val databaseName =  namespace
    var storage = storages.get(databaseName)

    if (storage == null) {
      Logger.info(s"Setting up storage for namespace $namespace")

      val database = connection(databaseName)
      storage = new MongoStorage(database)

      if (storages.putIfAbsent(databaseName, storage) == null) {
        Logger.info(s"Creating database for $namespace")

        // @todo write concerns? read preferences (nearest)?
        Logger.info(s"Setting up indexes for namespace $namespace")
        storage.ensureIndexes()
      }
    }
    storage
  }

  private def parseConf(): MongoConnection.ParsedURI = {
    config.getString("mongodb.uri") match {
      case Some(uri) =>
        MongoConnection.parseURI(uri) match {
          case Success(parsedURI) if parsedURI.db.isDefined =>
            parsedURI
          case Success(_) =>
            throw config.globalError(s"Missing database name in mongodb.uri '$uri'")
          case Failure(e) => throw config.globalError(s"Invalid mongodb.uri '$uri'", Some(e))
        }
      case _ =>
        throw config.globalError("Missing configuration key 'mongodb.uri'")
    }
  }

  def open() : Unit = {
    val parsedUri = parseConf()

    driver = new MongoDriver(Akka.system(current))
    connection = driver.connection(parsedUri)
  }

  def close() : Unit = {
    storages.clear()
  }
}

