package org.opencommercesearch.api

import org.opencommercesearch.api.models.Product

/**
 * This is a the base trait to decorate products with different data points (e.g. brand)
 */
trait ProductDecorator {

  def decorate(product: Product, fields: String*) : Unit
}
