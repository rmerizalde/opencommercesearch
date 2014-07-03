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
import org.opencommercesearch.client.ProductApi;
import org.opencommercesearch.client.ProductApiException;

import static org.junit.Assert.assertEquals;

/**
 * @author jmendez
 */
public class RequestTest {

  @Test
  public void testGetHttpRequest() throws ProductApiException {
	  BaseRequest ar = new BaseRequest() {

      @Override
      public String getEndpoint() {
        return null;
      }

      @Override
      public ProductApi.RequestMethod getMethod() {
        return null;
      }
    };

    ar.addParam("param1", "v11");
    ar.addParam("param2", "v21");
    ar.addParam("param1", "v12");

    String queryString = ar.getQueryString();
    assertEquals("param1=v11%2Cv12&param2=v21", queryString);

    ar.setParam("param1", "v13");
    queryString = ar.getQueryString();
    assertEquals("param1=v13&param2=v21", queryString);
  }
}
