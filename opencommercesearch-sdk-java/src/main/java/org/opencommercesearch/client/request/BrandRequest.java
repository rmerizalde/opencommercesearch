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

import org.opencommercesearch.client.ProductApi;

/**
 * A request to retrieve one or more products by id
 *
 * @author rmerizalde
 */
public class BrandRequest extends BaseRequest {

  private String endpoint;

  public BrandRequest(String id) {
    endpoint = "/brands/" + id;
  }

  @Override
  public String getEndpoint() {
    return endpoint;
  }

  @Override
  public ProductApi.RequestMethod getMethod() {
    return ProductApi.RequestMethod.GET;
  }
}
