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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jmendez
 */
public class BrowseBrandCategoryRequestTest {

  @Test
  public void testGetEndpoint() {
    BrowseBrandCategoryRequest request = new BrowseBrandCategoryRequest();
    assertEquals("/products", request.getEndpoint());
    request.setBrandId("00");
    assertEquals("/brands/00/products", request.getEndpoint());
    request.setCategoryId("cat00");
    assertEquals("/brands/00/categories/cat00/products", request.getEndpoint());
    request.setBrandId(null);
    assertEquals("/categories/cat00/products", request.getEndpoint());
  }
}
