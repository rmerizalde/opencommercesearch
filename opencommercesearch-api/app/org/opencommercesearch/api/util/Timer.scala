package org.opencommercesearch.api.util

/**
 * A convenient class to time operations
 *
 * @author rmerizalde
 */
case class Timer(startTime: Long = System.currentTimeMillis()) {
  def stop() = System.currentTimeMillis() - startTime
}
