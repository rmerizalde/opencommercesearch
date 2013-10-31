package org.opencommercesearch;

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

public class BrowseOptions {

    private boolean fetchCategoryGraph;
    private boolean fetchProducts;
    private boolean onSale;
    private boolean ruleBasedPage;
    private int maxCategoryResults;
    private String brandId;
    private String categoryId;
    private String categoryPath;
    private String catalogId;
    private int depthLimit = -1;
    private String separator;
    
    public BrowseOptions() {
    }
    
    public BrowseOptions(boolean fetchCategoryGraph, boolean fetchProducts,
            boolean onSale, boolean ruleBasedPage, int maxCategoryResults, String brandId,
            String categoryId, String categoryPath, String catalogId, int depthLimit, String separator) {
        this.fetchCategoryGraph = fetchCategoryGraph;
        this.fetchProducts = fetchProducts;
        this.onSale = onSale;
        this.ruleBasedPage = ruleBasedPage;
        this.maxCategoryResults = maxCategoryResults;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.categoryPath = categoryPath;
        this.catalogId = catalogId;
        this.depthLimit = depthLimit;
        this.separator = separator;
    }

    public boolean isFetchCategoryGraph() {
        return fetchCategoryGraph;
    }
    public void setFetchCategoryGraph(boolean fetchCategoryGraph) {
        this.fetchCategoryGraph = fetchCategoryGraph;
    }
    public boolean isFetchProducts() {
        return fetchProducts;
    }
    public void setFetchProducts(boolean fetchProducts) {
        this.fetchProducts = fetchProducts;
    }
    public boolean isOnSale() {
        return onSale;
    }
    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }
    public String getBrandId() {
        return brandId;
    }
    public void setBrandId(String brandId) {
        this.brandId = brandId;
    }
    public String getCategoryId() {
        return categoryId;
    }
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }
    public String getCategoryPath() {
        return categoryPath;
    }
    public void setCategoryPath(String categoryPath) {
        this.categoryPath = categoryPath;
    }
    public int getMaxCategoryResults() {
        return maxCategoryResults;
    }
    public void setMaxCategoryResults(int maxCategoryResults) {
        this.maxCategoryResults = maxCategoryResults;
    }
    public String getCatalogId() {
        return catalogId;
    }
    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }
    public boolean isRuleBasedPage() {
        return ruleBasedPage;
    }
    public void setRuleBasedPage(boolean ruleBasedPage) {
        this.ruleBasedPage = ruleBasedPage;
    }
    public int getDepthLimit() {
        return depthLimit;
    }
    public void setDepthLimit(int depthLimit) {
        this.depthLimit = depthLimit;
    }
    public String getSeparator() {
        return separator;
    }
    public void setSeparator(String separator) {
        this.separator = separator;
    }
    
}
