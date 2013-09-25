package org.opencommercesearch.api.impl

import org.opencommercesearch.api.ProductDecorator
import org.opencommercesearch.api.models.Product

import org.apache.solr.client.solrj.SolrQuery

class DefaultBrandDecorator extends ProductDecorator {

  def decorate(product: Product, fields: String*) : Unit = {
//    val query = new SolrQuery()

//    query.setRequestHandler(RealTimeRequestHandler)
//    query.add("id", id)
  }

}
