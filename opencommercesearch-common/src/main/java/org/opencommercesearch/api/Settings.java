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

public class Settings {
    private String host = "http://localhost:9000";
    private String brandsEndpoint = "/v1/brands";
    private String rulesEndpoint = "/v1/rules";
    private String productsEndpoint = "/v1/products";
    private boolean isPreview = false;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBrandsEndpoint() {
        return brandsEndpoint;
    }

    public void setBrandsEndpoint(String brandsEndpoint) {
        this.brandsEndpoint = brandsEndpoint;
    }

    public String getRulesEndpoint() {
        return rulesEndpoint;
    }

    public void setRulesEndpoint(String rulesEndpoint) {
        this.rulesEndpoint = rulesEndpoint;
    }

    public String getProductsEndpoint() {
        return productsEndpoint;
    }

    public void setProductsEndpoint(String productsEndpoint) {
        this.productsEndpoint = productsEndpoint;
    }

    public boolean getPreview() {
        return isPreview;
    }

    public void setPreview(boolean isPreview) {
        this.isPreview = isPreview;
    }

    public String getUrl4Endpoint(String endpoint) {
        return getUrl4Endpoint(endpoint, null);
    }

    public String getUrl4Endpoint(String endpoint, String id) {
        String endpointUrl = host + endpoint;

        if (id != null) {
            endpointUrl += "/" + id;
        }
        endpointUrl += (getPreview()? "?preview=true": "");
        return endpointUrl;
    }

}
