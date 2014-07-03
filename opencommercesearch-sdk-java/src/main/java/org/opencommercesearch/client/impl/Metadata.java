package org.opencommercesearch.client.impl;

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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Simple data holder for response metadata.
 *
 * @author jmendez
 */
public class Metadata {
    private int found;
    private int time;
    private JsonNode productSummary;
    private Facet[] facets;
    private BreadCrumb[] breadCrumbs;
    private String redirectUrl;

    /**
     * Gets the number of items found.
     *
     * @return The number of items found.
     */
    public int getFound() {
        return found;
    }

    protected void setFound(int found) {
        this.found = found;
    }

    /**
     * Gets the time in milliseconds that the API server took to respond.
     *
     * @return The time in milliseconds that the API server took to respond.
     */
    public int getTime() {
        return time;
    }

    protected void setTime(int time) {
        this.time = time;
    }

    public JsonNode getProductSummary() {
        return productSummary;
    }

    public void setProductSummary(JsonNode productSummary) {
        this.productSummary = productSummary;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public Facet[] getFacets() {
        return facets;
    }

    protected void setFacets(Facet[] facets) {
        this.facets = facets;
    }

    public BreadCrumb[] getBreadCrumbs() {
        return breadCrumbs;
    }

    protected void setBreadCrumbs(BreadCrumb[] breadCrumbs) {
        this.breadCrumbs = breadCrumbs;
    }


}
