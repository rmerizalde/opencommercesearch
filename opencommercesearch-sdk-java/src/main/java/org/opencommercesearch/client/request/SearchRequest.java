package org.opencommercesearch.client.request;

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

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.client.ProductApi;

/**
 * Holds the necessary parameters to perform product searches on the API.
 * <p/>
 * Once populated, pass this class to {@link org.opencommercesearch.client.ProductApi} to get the search response.
 *
 * @author jmendez
 */
public class SearchRequest extends BaseRequest {

  public SearchRequest() {}

  public SearchRequest(String query) {
    setQuery(query);
  }

  public void setQuery(String query) {
    setParam("q", query);
  }

  public void setOutlet(boolean outlet) {
    setParam("outlet", String.valueOf(outlet));
  }

  public void setOffset(int offset) {
    setParam("offset", String.valueOf(offset));
  }

  public void setLimit(int limit) {
    setParam("limit", String.valueOf(limit));
  }


  public void addFilterQuery(String filterQuery) {
    addParam("filterQueries", filterQuery, FILTER_QUERY_SEPARATOR);
  }

  public void setFilterQueries(String[] filterQueries) {
    if (filterQueries == null) {
      throw new NullPointerException("filterQueries");
    }

    setParam("filterQueries", StringUtils.join(filterQueries, FILTER_QUERY_SEPARATOR));
  }

  public void setSort(String sort) {
    setParam("sort", sort);
  }

  @Override
  public String getEndpoint() {
    return "/products";
  }

  @Override
  public ProductApi.RequestMethod getMethod() {
    return ProductApi.RequestMethod.GET;
  }
}
