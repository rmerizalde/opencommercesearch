package org.opencommercesearch.api.common

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

import org.apache.solr.client.solrj.SolrQuery

/**
 * This trait provides subclasses with functionality to
 * add to the solr query facets
 *
 * @author gsegura
 */
trait FacetQuery {

  def withFieldFacet(facetField: String, facetLimit: Int, rows:Int, query: SolrQuery) : SolrQuery = {
    query.setRows(rows);
    query.setFacet(true)
    query.addFacetField(facetField)
    query.setFacetLimit(facetLimit);
    query.setFacetMinCount(1);
  }
}
