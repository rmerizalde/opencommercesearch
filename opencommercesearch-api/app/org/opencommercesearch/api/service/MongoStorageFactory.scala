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
import com.mongodb._
import play.api.{Logger, Configuration}
import org.jongo.{Jongo, Mapper}
import com.mongodb.gridfs.GridFS
import org.jongo.marshall.jackson.JacksonMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * MongoDB storage factory implementation
 *
 * @author rmerizalde
 */
class MongoStorageFactory extends StorageFactory[WriteResult] {

  val storages = new ConcurrentHashMap[String, MongoStorage]()
  var mongo: MongoClient = null
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

      val jongoUri = config.getString("jongo.uri").getOrElse("mongodb://127.0.0.1:27017/")
      val uri = new MongoClientURI(jongoUri)

      this.synchronized {
        if (mongo == null) {
          Logger.info(s"Initializing mongo client singleton for namespace $namespace with URI $jongoUri")
          mongo = new MongoClient(uri)
        }
      }

      storage = new MongoStorage(mongo)
      if (storages.putIfAbsent(databaseName, storage) == null) {
        Logger.info(s"Creating database for $namespace")
        val db = mongo.getDB(databaseName)

        val defaultWriteConcern = config.getString("jongo.defaultWriteConcern")
        for (writeConcern <- defaultWriteConcern) {
          Logger.info(s"Setting default write concern to $writeConcern for namespace $namespace")
          db.setWriteConcern(WriteConcern.valueOf(writeConcern))
        }

        val readPreference = config.getString("jongo.defaultReadPreference").getOrElse("nearest")
        db.setReadPreference(ReadPreference.valueOf(readPreference))
        if (uri.getUsername() != null) {
          Logger.info(s"Authenticating using username ${uri.getUsername()} for namespace $namespace")
          db.authenticate(uri.getUsername(), uri.getPassword())
        }

        val jongo = new Jongo(db, createMapper())

        storage.setJongo(jongo)
        if (config.getBoolean("jongo.gridfs.enabled").getOrElse(false)) {
          Logger.info(s"Setting up GridFS instance for namespace $namespace")
          storage.setGridFs(new GridFS(db))
        }
        Logger.info(s"Setting up indexes for namespace $namespace")
        storage.ensureIndexes
      } else {
        Logger.info(s"Connection already in progress, closing storage for namespace $namespace")
        mongo.close()
      }
    }
    storage
  }

  private def createMapper() : Mapper = {
    new JacksonMapper.Builder().registerModule(new DefaultScalaModule).build()
  }

  def close : Unit = {
    if (mongo != null) {
      Logger.info("Closing mongo client")
      mongo.close()
    }
  }

}
