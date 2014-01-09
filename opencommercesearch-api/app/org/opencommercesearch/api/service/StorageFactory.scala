package org.opencommercesearch.api.service

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
