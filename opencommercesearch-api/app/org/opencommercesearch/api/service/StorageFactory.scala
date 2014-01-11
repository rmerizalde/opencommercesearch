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

import play.api.Configuration

/**
 * A storage factory
 *
 * @author rmerizalde
 */
trait StorageFactory[T] {

  /**
   * Sets the configuration
   * @param config is Play's configuration object
   */
  def setConfig(config: Configuration)

  /**
   * Sets the class loader for the storage layer.
   * @param classLoader is the classloader the storage should use
   */
  def setClassLoader(classLoader: ClassLoader)

  /**
   * Returns the storage instance for the given namespace. Each implementation can interpret this namespace differently.
   *
   * @param namespace is the namespace for the storage
   * @return  the storage instance for the given namespace
   */
  def getInstance(namespace: String) : Storage[T]

  /**
   * Closes all storage instances created by this factory
   */
  def close : Unit
}
