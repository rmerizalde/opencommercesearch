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

  /**
   * Sets the parameters required to do facet searches on a field.
   * <br/><br/>
   * This method should be called when only facets are needed (i.e. find all brands on the products catalog), not
   * to return actual search results.
   * @param facetField Field to facet on
   * @param query Solr query to add new parameters to
   * @return Solr query with necessary facet parameters
   */
  def withFieldFacet(facetField: String, query: SolrQuery) : SolrQuery = {
    query.setRows(0);
    query.setFacet(true)
    query.addFacetField(facetField)
    query.setFacetMinCount(1);
  }
}
