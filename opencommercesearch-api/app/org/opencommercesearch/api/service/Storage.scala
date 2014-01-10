package org.opencommercesearch.api.service

import org.opencommercesearch.api.models.Product
import scala.concurrent.Future

/**
 * Represents the storage for catalog object including products, brand and categories
 *
 * @author rmerizalde
 */
trait Storage[T] {


  /**
   * Finds the product with the given id. Optionally, takes a list of fields to return
   * @param id is the product id
   * @param fields is the list of fields to return
   * @return
   */
  def findProduct(id: String, fields: Seq[String]) : Future[Product]

  /**
   * Finds the product with the given id for the given site. If the product exists but none of its skus are assigned to
   * site null is return
   *
   * @param id is the product id
   * @param site is the site id
   * @param fields is the list of fields to return
   * @return
   */
  def findProduct(id: String, site: String, fields: Seq[String]) : Future[Product]

  /**
   * Saves the given list of products. Returns the result of the last write
   * @param product is one or more products to store
   * @return the results of writing the last product
   */
  def save(product: Product*) : Future[T]

  /**
   * Releases the resources used by this storage
   */
  def close : Unit

}
