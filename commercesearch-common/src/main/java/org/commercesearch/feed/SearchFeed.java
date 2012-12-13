package org.commercesearch.feed;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.commercesearch.SearchServer;

import atg.commerce.inventory.InventoryException;
import atg.nucleus.GenericService;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;

/**
 * This class provides a basic functionality to generate a search feed. This includes:
 *  - Product loading
 *  - Category tokens
 *
 * @TODO implement default feed functionality
 */
public abstract class SearchFeed extends GenericService {

    private SearchServer searchServer;
    private Repository productRepository;
    private String productItemDescriptorName;
    private RqlStatement productCountRql;
    private RqlStatement productRql;
    private int productBatchSize;

    public SearchServer getSearchServer() {
        return searchServer;
    }

    public void setSearchServer(SearchServer searchServer) {
        this.searchServer = searchServer;
    }

    public Repository getProductRepository() {
        return productRepository;
    }

    public void setProductRepository(Repository productRepository) {
        this.productRepository = productRepository;
    }

    public String getProductItemDescriptorName() {
        return productItemDescriptorName;
    }

    public void setProductItemDescriptorName(String productItemDescriptorName) {
        this.productItemDescriptorName = productItemDescriptorName;
    }

    public RqlStatement getProductCountRql() {
        return productCountRql;
    }

    public void setProductCountRql(RqlStatement productCountRql) {
        this.productCountRql = productCountRql;
    }

    public RqlStatement getProductRql() {
        return productRql;
    }

    public void setProductRql(RqlStatement productRql) {
        this.productRql = productRql;
    }

    public int getProductBatchSize() {
        return productBatchSize;
    }

    public void setProductBatchSize(int productBatchSize) {
        this.productBatchSize = productBatchSize;
    }

    public boolean isProductIndexable(RepositoryItem product) {
        return true;
    }

    public boolean isSkuIndexable(String sku) throws InventoryException {
        return true;
    }

    public boolean isCategoryIndexable(RepositoryItem category) {
        return true;
    }
 
    public void startFullFeed() throws IOException, SolrServerException, RepositoryException, SQLException,
            InventoryException {
        long startTime = System.currentTimeMillis();

        RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
        int productCount = productRql.executeCountQuery(productView, null);

        if (isLoggingInfo()) {
            logInfo("Started full feed for " + productCount + " products");
        }
        feedStarted();
        // temporal
        getSearchServer().deleteByQuery("*:*");
        getSearchServer().commit();
        // temporal
        
        int processedProductCount = 0;
        int indexedProductCount = 0;

        Integer[] rqlArgs = new Integer[] { 0, getProductBatchSize() };
        RepositoryItem[] products = productRql.executeQueryUncached(productView, rqlArgs);
        List<SolrInputDocument> documents = new ArrayList<SolrInputDocument>();
        
        while (products != null) {
            for (RepositoryItem product : products) {
                if (isProductIndexable(product)) {
                    processProduct(product, documents);
                }
                processedProductCount++;
            }

            if (documents.size() > 0) {
                getSearchServer().add(documents);
                getSearchServer().commit();
                documents = new ArrayList<SolrInputDocument>();
            }
            
            rqlArgs[0] += getProductBatchSize();
            products = productRql.executeQueryUncached(productView, rqlArgs);

            if (isLoggingInfo()) {
                logInfo("Processed " + processedProductCount  + " out of " + productCount);
                logInfo("Indexed "+ indexedProductCount + " products");
            }
        }

        feedFinished();
        if (isLoggingInfo()) {
            logInfo("Full feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                    + indexedProductCount + " products were indexable from  " + processedProductCount
                    + " processed products");
        }
    }

    protected abstract void cleanupDocuments(SearchServer searchServer, List<String> documentsToDelete);

    protected abstract void feedStarted();

    protected abstract void feedFinished();

    protected abstract void processProduct(RepositoryItem product, List<SolrInputDocument> documents)
            throws RepositoryException, InventoryException;

    /**
     * Generate the category tokens to create a hierarchical facet in Solr. Each
     * token is formatted such that encodes the depth information for each node
     * that appears as part of the path, and include the hierarchy separated by
     * a common separator (depth/first level category name/second level
     * category name/etc)
     * 
     * @param document
     *            The document to set the attributes to.
     * @param product
     *            The RepositoryItem for the product item descriptor
     * @param catalogAssignments
     *            If the product is belongs to a category in any of those
     *            catalogs then that category is part of the returned value.
     */
    protected void loadCategoryPaths(SolrInputDocument document, RepositoryItem product,
            Set<RepositoryItem> catalogAssignments, Set<RepositoryItem> categoryCatalogs) {
        if (product != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<RepositoryItem> productCategories = (Set<RepositoryItem>) product
                        .getPropertyValue("parentCategories");

                if (productCategories != null) {
                    List<RepositoryItem> categoryIds = new ArrayList<RepositoryItem>();
                    for (RepositoryItem productCategory : productCategories) {
                        if (isCategoryInCatalogs(productCategory, catalogAssignments)) {
                            if (isCategoryIndexable(productCategory)) {
                                loadCategoryPaths(document, productCategory, categoryIds, catalogAssignments);
                            }
                            document.addField("categoryId", productCategory.getRepositoryId());

                            if (categoryCatalogs != null) {
                                Set<RepositoryItem> catalogs = (Set<RepositoryItem>) productCategory.getPropertyValue("catalogs");
                                for(RepositoryItem catalog : catalogs){
                                    if(catalogAssignments.contains(catalog)){
                                        categoryCatalogs.add(catalog);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                if (isLoggingError()) {
                    logError("Problem generating the categoryids attribute", ex);
                }
            }
        }
    }

    /**
     * Helper method to test if category is assigned to and of catalogs in the
     * given set
     * 
     * @param category
     *            the category to be tested
     * @param catalogs
     *            the set of categories to search in
     * @return
     */
    private boolean isCategoryInCatalogs(RepositoryItem category, Set<RepositoryItem> catalogs) {

        if (catalogs == null || catalogs.size() == 0) {
            return false;
        }
        
        boolean isAssigned = false;
        
        Set<RepositoryItem> categoryCatalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs"); 
        if (categoryCatalogs != null) { 
            for (RepositoryItem categoryCatalog : categoryCatalogs) { 
                if (catalogs.contains(categoryCatalog)) { 
                    isAssigned = true;
                    break; 
                } 
            } 
        }
        
        return isAssigned;
    }

    /**
     * Helper method to generate the category tokens recursively
     * 
     * 
     * @param document
     *            The document to set the attributes to.
     * @param category
     *            The repositoryItem of the current level
     * @param hierarchyCategories
     *            The list where we store the categories during the recursion
     * @param catalogAssignments
     *            The list of catalogs to restrict the category token generation
     */
    private void loadCategoryPaths(SolrInputDocument document, RepositoryItem category,
            List<RepositoryItem> hierarchyCategories, Set<RepositoryItem> catalogAssignments) {
        Set<RepositoryItem> parentCategories = (Set<RepositoryItem>) category.getPropertyValue("fixedParentCategories");

        if (parentCategories != null && parentCategories.size() > 0) {
            hierarchyCategories.add(0, category);
            for (RepositoryItem parentCategory : parentCategories) {
                loadCategoryPaths(document, parentCategory, hierarchyCategories, catalogAssignments);
            }
            hierarchyCategories.remove(0);
        } else {
            Set<RepositoryItem> catalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs");
            for(RepositoryItem catalog : catalogs){
                if(catalogAssignments.contains(catalog)){
                    generateCategoryTokens(document, hierarchyCategories, catalog.getRepositoryId());
                }
            }
        }
    }

    /**
     * Generates category tokens into a multivalued field called category. Each
     * token has the format: depth/catalog/category 1/,,,.categirt N, For
     * example:
     * 
     * 0/bcs 1/bcs/Men's Clothing 2/bcs/Men's Clothing/Men's Jackets 3/bcs/Men's
     * Clothing/Men's Jackets/Men's Casual Jacekt's
     * 
     * @param document
     *            The document to set the attributes to.
     * @param hierarchyCategories
     *
     * @param catalog
     *            
     */
    private void generateCategoryTokens(SolrInputDocument document, List<RepositoryItem> hierarchyCategories,
            String catalog) {
        if (hierarchyCategories == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= hierarchyCategories.size(); i++) {
            builder.append(i).append(".").append(catalog).append(".");
            for (int j = 0; j < i; j++) {
                builder.append(hierarchyCategories.get(j).getItemDisplayName()).append(".");
            }
            builder.setLength(builder.length() - 1);
            document.addField("category", builder.toString());
            builder.setLength(0);
        }
    }
    
}
