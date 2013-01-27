package org.opencommercesearch;

public class BrowseOptions {

    private boolean fetchCategoryGraph;
    private boolean fetchProducts;
    private boolean onSale;
    private int maxCategoryResults;
    private String brandId;
    private String categoryId;
    private String categoryPath;
    private String catalogId;
    
    public BrowseOptions() {
    }
    
    public BrowseOptions(boolean fetchCategoryGraph, boolean fetchProducts,
            boolean onSale, int maxCategoryResults, String brandId,
            String categoryId, String categoryPath, String catalogId) {
        this.fetchCategoryGraph = fetchCategoryGraph;
        this.fetchProducts = fetchProducts;
        this.onSale = onSale;
        this.maxCategoryResults = maxCategoryResults;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.categoryPath = categoryPath;
        this.catalogId = catalogId;
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
    
}
