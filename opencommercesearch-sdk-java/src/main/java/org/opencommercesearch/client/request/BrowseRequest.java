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

/**
 * Holds the necessary parameters to browse products.
 * <p/>
 * Once populated, pass this class to {@link org.opencommercesearch.client.ProductApi} to get the browse response.
 */
public abstract class BrowseRequest extends SearchRequest {
  String brandId;
  String categoryId;

  @Override
  public String getEndpoint() {
    String brandPart = StringUtils.EMPTY;
    String categoryPart = StringUtils.EMPTY;

    if (brandId != null) {
      brandPart = "/brands/" + brandId;
    }
    if (categoryId != null) {
      categoryPart = "/categories/" + categoryId;
    }

    //If no brand ID nor category ID, then do a simple search.
    return brandPart + categoryPart + "/products";
  }
}
