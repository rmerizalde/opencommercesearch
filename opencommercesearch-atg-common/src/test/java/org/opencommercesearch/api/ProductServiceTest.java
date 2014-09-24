package org.opencommercesearch.api;

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


import atg.nucleus.ServiceException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restlet.Client;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

import static org.opencommercesearch.api.ProductService.Endpoint.*;

public class ProductServiceTest {

    private ProductService productService = new ProductService();
    private ProductService previewProductService = new ProductService();

    @Before
    public void setup() {
        Map<String, String> endpoints = new HashMap<String, String>(4);

        endpoints.put("brands", "/v1/brands");
        endpoints.put("rules", "/v1/rules");
        endpoints.put("products", "/v1/products");
        productService.setEndpoints(endpoints);
        previewProductService.setHost("http://preview.localhost:9000");
        previewProductService.setPreview(true);
        previewProductService.setEndpoints(endpoints);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingEndpoint() {
        productService.getUrl4Endpoint(CATEGORIES);
    }

    @Test
    public void testEndpointUrl() {
        assertEquals("http://localhost:9000/v1/brands", productService.getUrl4Endpoint(BRANDS));
    }

    @Test
    public void testPreviewEndpointUrl() {
        assertEquals("http://preview.localhost:9000/v1/rules?preview=true", previewProductService.getUrl4Endpoint(RULES));
    }

    @Test
    public void testEndpointUrlWithId() {
        assertEquals("http://localhost:9000/v1/products/PRD0001", productService.getUrl4Endpoint(PRODUCTS, "PRD0001"));
    }

    @Test
    public void testPreviewEndpointUrlWithId() {
        assertEquals("http://preview.localhost:9000/v1/products/PRD0001?preview=true", previewProductService.getUrl4Endpoint(PRODUCTS, "PRD0001"));
    }
}
