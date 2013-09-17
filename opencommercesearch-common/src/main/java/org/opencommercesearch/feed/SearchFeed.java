package org.opencommercesearch.feed;

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

import atg.commerce.inventory.InventoryException;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.codehaus.jackson.map.ObjectMapper;
import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.api.Settings;
import org.opencommercesearch.model.Product;
import org.opencommercesearch.model.ProductList;
import org.opencommercesearch.model.Sku;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import org.opencommercesearch.service.localeservice.FeedLocaleService;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.*;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.StringRepresentation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opencommercesearch.Utils.errorMessage;

/**
 * This class provides a basic functionality to generate a search feed. This includes:
 *  - Product loading
 *  - Category tokens
 *
 * @TODO implement default feed functionality
 */
public abstract class SearchFeed extends GenericService {
    private static SendQueueItem POISON_PILL = new SendQueueItem();

    private Repository productRepository;
    private String productItemDescriptorName;
    private RqlStatement productCountRql;
    private RqlStatement productRql;
    private int productBatchSize;
    private int indexBatchSize;
    private Settings apiSettings;
    private ObjectMapper mapper;
    private Client client;
    private String endpointUrl;
    private int workerCount;
    private ExecutorService productTaskExecutor;
    private AtomicInteger processedProductCount;
    private AtomicInteger indexedProductCount;
    private ExecutorService sendTaskExecutor;
    private BlockingDeque<SendQueueItem> sendQueue;
    private volatile boolean running;
    private Map<Thread, Map<String,StopWatch>> timersByThread;
    private FeedLocaleService localeService;

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

    public int getIndexBatchSize() {
        return indexBatchSize;
    }

    public void setIndexBatchSize(int indexBatchSize) {
        this.indexBatchSize = indexBatchSize;
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

    public Settings getApiSettings() {
        return apiSettings;
    }

    public void setApiSettings(Settings apiSettings) {
        this.apiSettings = apiSettings;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    public Map<Thread, Map<String,StopWatch>> getTimers() {
         return timersByThread;
    }

    public int getCurrentProcessedProductCount() {
        return processedProductCount.get();
    }

    public int getCurrentIndexedProductCount() {
        return indexedProductCount.get();
    }

    public int getCurrentSendQueueSize() {
        return sendQueue.size();
    }

    public FeedLocaleService getLocaleService() {
        return localeService;
    }

    public void setLocaleService(FeedLocaleService localeService) {
        this.localeService = localeService;
    }

    // Returns the timers for the current threads
    private Map<String, StopWatch> getCurrentTimers() {
        if (timersByThread == null) {
            timersByThread = new ConcurrentHashMap<Thread, Map<String, StopWatch>>(getWorkerCount());
        }

        Map<String, StopWatch> timers = timersByThread.get(Thread.currentThread());

        if (timers == null) {
            timers = new LinkedHashMap<String, StopWatch>();
            timersByThread.put(Thread.currentThread(), timers);
        }
        return timers;
    }

    public void startTimer(String key) {
        Map<String, StopWatch> timers = getCurrentTimers();
        StopWatch timer = timers.get(key);
        if (timer == null) {
            timer = new StopWatch();
            timer.start();
            timers.put(key, timer);
        } else {
            timer.resume();
        }
    }

    public void stopTimer(String key) {
        Map<String, StopWatch> timers = getCurrentTimers();
        timers.get(key).suspend();
    }

    public void stopAllTimers() {
        for (Map.Entry<Thread, Map<String, StopWatch>> entry : timersByThread.entrySet()) {
            for (StopWatch timer : entry.getValue().values()) {
                timer.stop();
            }
        }
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        client = new Client(Protocol.HTTP);
        endpointUrl = getApiSettings().getUrl4Endpoint(getApiSettings().getProductsEndpoint());
        mapper = new ObjectMapper();
        if (getWorkerCount() <= 0) {
            if (isLoggingInfo()) {
                logInfo("At least one worker is required to process the feed, setting number of workers to 1");
                setWorkerCount(1);
            }
        }
        productTaskExecutor = Executors.newFixedThreadPool(getWorkerCount());
        processedProductCount = new AtomicInteger(0);
        indexedProductCount = new AtomicInteger(0);
        timersByThread = new ConcurrentHashMap<Thread, Map<String, StopWatch>>(getWorkerCount());
        sendTaskExecutor = Executors.newSingleThreadExecutor();
        sendQueue = new LinkedBlockingDeque<SendQueueItem>();
    }

    @Override
    public void doStopService() throws ServiceException {
        terminate();
        productTaskExecutor.shutdown();
        sendTaskExecutor.shutdown();
    }

    public void terminate() {
        running = false;
    }

    /**
     * A task to process the products in a product catalog partition.
     */
    private class ProductPartitionTask implements Runnable {
        private CountDownLatch endGate;
        private int offset;
        private int limit;
        private long feedTimestamp;
        private String name;

        ProductPartitionTask(int offset, int limit, long feedTimestamp, CountDownLatch endGate) {
            this.offset = offset;
            this.limit = limit;
            this.feedTimestamp = feedTimestamp;
            this.endGate = endGate;
            this.name = offset + " - " + (offset + limit);
        }

        public String getName() {
            return name;
        }

        public void run() {
            try {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Started processing partition " + getName());
                }

                Integer[] rqlArgs = new Integer[] { offset, getProductBatchSize() };
                RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
                RepositoryItem[] productItems = productRql.executeQueryUncached(productView, rqlArgs);
                SearchFeedProducts products = new SearchFeedProducts();
                int productCount = limit;
                int localProductProcessedCount = 0;
                boolean done = false;

                while (running && !done && productItems != null) {
                    for (RepositoryItem product : productItems) {
                        if (isProductIndexable(product)) {
                            try {
                                startTimer("processProduct");
                                processProduct(product, products);
                            } finally {
                                stopTimer("processProduct");
                            }
                            indexedProductCount.incrementAndGet();
                            sendProducts(products, feedTimestamp, getIndexBatchSize(), true);
                        }
                        processedProductCount.incrementAndGet();
                        localProductProcessedCount++;

                        done = localProductProcessedCount >= limit;
                        if (done) break;
                    }

                    if (!done) {
                        rqlArgs[0] += getProductBatchSize();
                        productItems = productRql.executeQueryUncached(productView, rqlArgs);
                    }

                    if (isLoggingInfo()) {
                        logInfo(Thread.currentThread() + " - Processed " + processedProductCount.get()  + " out of " + productCount + " by partition " + getName());
                        logInfo(Thread.currentThread() + " - Indexable products "+ indexedProductCount.get());
                    }
                }

                sendProducts(products, feedTimestamp, 0, true);
            } catch (RepositoryException ex) {
                if (isLoggingError()) {
                    logError("Exception processing catalog partition: " + getName(), ex);
                }
            } catch (InventoryException ex) {
                if (isLoggingError()) {
                    logError("Exception processing catalog partition: " + getName(), ex);
                }
            } finally {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Finished processing partition " + getName());
                }
                endGate.countDown();
            }
        }
    }

    private static class SendQueueItem {
        Locale locale;
        ProductList productList;

        SendQueueItem() {}

        SendQueueItem(Locale locale, ProductList productList) {
            this.locale = locale;
            this.productList = productList;
        }
    }

    private class SendTask implements Runnable {
        private CountDownLatch endGate;

        public SendTask(CountDownLatch endGate) {
            this.endGate = endGate;
        }

        public void run() {
            try {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Started send task");
                }

                while (running) {
                    SendQueueItem item = sendQueue.take();

                    if (POISON_PILL == item) {
                        break;
                    }

                    sendProducts(item.locale.getLanguage(), item.productList);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Finished send task");
                }
                endGate.countDown();
            }
        }
    }

    /**
     * Sends the products for indexing
     *
     * @param products the lists of products be indexed
     * @param feedTimestamp
     * @param min the minimum size of of a product list. If the size is not met then the products are not sent
     *            for indexing
     * @param async determines if the products should be send right away or asynchronously.
     */
    public void sendProducts(SearchFeedProducts products, long feedTimestamp, int min, boolean async) {
        startTimer("sendProducts");
        for (Locale locale : products.getLocales()) {
            if (products.getSkuCount(locale) > min) {
                List<Product> productList = products.getProducts(locale);
                try {
                    if (async) {
                        List<Product> clone = new ArrayList<Product>(productList.size());
                        clone.addAll(productList);
                        sendQueue.offer(new SendQueueItem(locale, new ProductList(clone, feedTimestamp)));
                    } else {
                        sendProducts(locale.getLanguage(), new ProductList(productList, feedTimestamp));
                    }
                } finally {
                    productList.clear();
                }
            }
        }
        stopTimer("sendProducts");
    }

    // helper method that actually sends the products for indexing
    private void sendProducts(String language, ProductList productList) {
        startTimer("sendProductsToApi");
        try {
            String json = null;
            try {
                startTimer("sendProductsToApi.generateJson");
                json = getObjectMapper().writeValueAsString(productList);
            } catch (IOException ex) {
                if (isLoggingDebug()) {
                    logDebug("Unable to convert product list to JSON");
                }
            } finally {
                stopTimer("sendProductsToApi.generateJson");
            }

            final StringRepresentation jsonRepresentation = new StringRepresentation(json, MediaType.APPLICATION_JSON);
            final Request request = new Request(Method.PUT, endpointUrl, new EncodeRepresentation(Encoding.GZIP, jsonRepresentation));
            final ClientInfo clientInfo = request.getClientInfo();
            clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(language))));
            startTimer("sendProductsToApi.sendJson");
            final Response response = client.handle(request);

            if (!response.getStatus().equals(Status.SUCCESS_CREATED)) {
                if (isLoggingInfo()) {
                    logInfo("Sending products [" + productsId(productList.getProducts()) + "] fail with status: " + response.getStatus() + " ["
                            + errorMessage(response.getEntity()) + "]");
                }
                onProductsSent(response, productList.getProducts());
            } else {
                onProductsSentError(productList.getProducts());
            }
        } catch (Exception ex) {
            if (isLoggingInfo()) {
                logInfo("Sending products [" + productsId(productList.getProducts()) + "] failed with unexpected exception", ex);
            }
            onProductsSentError(productList.getProducts());
        } finally {
            stopTimer("sendProductsToApi.sendJson");
            stopTimer("sendProductsToApi");
            productList.getProducts().clear();
        }
    }

    /**
     * Deletes the product with the given id from index
     *
     * @param id is the id of the product to be deleted
     */
    public void delete(String id) {
        Set<String> languages = new HashSet<String>();
         for (Locale locale : localeService.getSupportedLocales()) {
             if (!languages.contains(locale.getLanguage())) {
                try {
                    final Request request = new Request(Method.DELETE, getApiSettings().getUrl4Endpoint(getApiSettings().getProductsEndpoint(), id));
                    final ClientInfo clientInfo = request.getClientInfo();
                    clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(locale.getLanguage()))));
                    final Response response = client.handle(request);

                    if (isLoggingInfo()) {
                        if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                            logInfo("Successfully deleted product " + id + " for " + locale.getLanguage());
                        } else {
                            logInfo("Deleting product " + id + " for " + locale.getLanguage() + " failed with status: " + response.getStatus());
                        }
                    }
                    languages.add(locale.getLanguage());
                } catch (Exception ex) {
                    if (isLoggingError()) {
                        logError("Deleting product " + id + " failed", ex);
                    }
                }
             }
         }
    }

    public void delete(long feedTimestamp) {
        Set<String> languages = new HashSet<String>();
        for (Locale locale : localeService.getSupportedLocales()) {
            if (!languages.contains(locale.getLanguage())) {
                try {
                    String endpointUrl = getApiSettings().getUrl4Endpoint(getApiSettings().getProductsEndpoint());

                    if (endpointUrl.indexOf("?") != -1) {
                        endpointUrl += "&";
                    }
                    endpointUrl += "feedTimestamp=" + feedTimestamp;
                    final Request request = new Request(Method.DELETE, endpointUrl);
                    final ClientInfo clientInfo = request.getClientInfo();
                    clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(locale.getLanguage()))));
                    final Response response = client.handle(request);

                    if (isLoggingInfo()) {
                        if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                            logInfo("Successfully deleted products for " + locale.getLanguage() + " with index timestamp before to "
                                    + feedTimestamp);
                        } else {
                            logInfo("Deleting products for " + locale.getLanguage() + " with index timestamp before to "
                                    + feedTimestamp + " failed with status: " + response.getStatus());
                        }
                    }
                    languages.add(locale.getLanguage());
                } catch (Exception ex) {
                    if (isLoggingError()) {
                        logError("Deleting products for " + locale.getLanguage() + " with index timestamp before to "
                                + feedTimestamp + " failed", ex);
                    }
                }
            }
        }
    }

    public void startFullFeed() throws SearchServerException, RepositoryException, SQLException,
            InventoryException, InterruptedException {
        if (running){
            if (isLoggingInfo()) {
                logInfo("The feed is currently running, aborting...");
            }
            return;
        }

        try {
            timersByThread.clear();
            startTimer("startFullFeed");
            running = true;
            final long startTime = System.currentTimeMillis();

            RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
            int productCount = productRql.executeCountQuery(productView, null);

            if (isLoggingInfo()) {
                logInfo("Started full feed for " + productCount + " products");
            }

            final long feedTimestamp = System.currentTimeMillis();

            onFeedStarted(feedTimestamp);
            processedProductCount.set(0);
            indexedProductCount.set(0);

            // create send worker
            final CountDownLatch sendEndGate = new CountDownLatch(1);
            sendTaskExecutor.execute(new SendTask(sendEndGate));
            // create a partition for each worker
            final CountDownLatch endGate = new CountDownLatch(workerCount);
            int partitionSize = productCount / getWorkerCount();
            for (int i = 0; i < getWorkerCount(); i++) {
                int offset = i * partitionSize;
                int limit = partitionSize;

                if (productCount - limit < partitionSize) {
                    limit += productCount - limit;
                }

                productTaskExecutor.execute(new ProductPartitionTask(offset, limit, feedTimestamp, endGate));
                if (isLoggingInfo()) {
                    logInfo("Catalog partition created: " + offset + " - " + limit);
                }
            }

            if (isLoggingInfo()) {
                logInfo("Waiting for workers to finish...");
            }
            endGate.await();
            if (isLoggingInfo()) {
                logInfo("Waiting for send worker to finish...");
            }
            sendQueue.offer(POISON_PILL);
            sendEndGate.await();
            if (running) {
                delete(feedTimestamp);
                onFeedFinished(feedTimestamp);
                stopTimer("startFullFeed");
                stopAllTimers();

                if (isLoggingInfo()) {
                    logInfo("Full feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                            + indexedProductCount.get() + " products were indexable from  " + processedProductCount.get()
                            + " processed products");
                }
            } else {
                if (isLoggingInfo()) {
                    logInfo("Full feed was terminated");
                }
            }
        } finally {
            running = false;
        }
    }

    private String productsId(List<Product> products) {
        if (products == null && products.size() == 0) {
            return StringUtils.EMPTY;
        }
        StringBuilder buffer = new StringBuilder();

        for (Product product : products) {
            buffer.append(product.getId()).append(", ");
        }
        buffer.setLength(buffer.length() - 2);
        return buffer.toString();
    }
    
    protected abstract void onFeedStarted(long indexStamp);

    protected abstract void onProductsSent(Response response, List<Product> productList);

    protected abstract void onProductsSentError(List<Product> productList);

    protected abstract void onFeedFinished(long indexStamp);

    protected abstract void processProduct(RepositoryItem product, SearchFeedProducts products)
            throws RepositoryException, InventoryException;

    /**
     * Generate the category tokens to create a hierarchical facet in Solr. Each
     * token is formatted such that encodes the depth information for each node
     * that appears as part of the path, and include the hierarchy separated by
     * a common separator (depth/first level category name/second level
     * category name/etc)
     * 
     * @param sku
     *            The document to set the attributes to.
     * @param product
     *            The RepositoryItem for the product item descriptor
     * @param skuCatalogAssignments
     *            If the product is belongs to a category in any of those
     *            catalogs then that category is part of the returned value.
     */
    protected void loadCategoryPaths(Sku sku, RepositoryItem product,
            Set<RepositoryItem> skuCatalogAssignments, Set<RepositoryItem> categoryCatalogs) {
        startTimer("processProduct.sku.loadCategoryPaths");
        if (product != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<RepositoryItem> productCategories = (Set<RepositoryItem>) product
                        .getPropertyValue("parentCategories");
                Set<String> tokenCache = new HashSet<String>();
                Set<String> ancestorCache = new HashSet<String>();
                Set<String> leafCache = new HashSet<String>();
                
                if (productCategories != null) {
                    List<RepositoryItem> categoryIds = new ArrayList<RepositoryItem>();
                    for (RepositoryItem productCategory : productCategories) {
                        if (isCategoryInCatalogs(productCategory, skuCatalogAssignments)) {
                            if (isRulesCategory(productCategory)) {
                                sku.addAncestorCategory(productCategory.getRepositoryId());
                            } else if (isCategoryIndexable(productCategory)) {
                                loadCategoryPathsAndAncestorIds(sku, productCategory, categoryIds, skuCatalogAssignments, tokenCache, ancestorCache);
                            }

                            if (categoryCatalogs != null) {
                                Set<RepositoryItem> catalogs = (Set<RepositoryItem>) productCategory.getPropertyValue("catalogs");
                                for(RepositoryItem catalog : catalogs){
                                    if(skuCatalogAssignments.contains(catalog)){
                                        categoryCatalogs.add(catalog);
                                    }
                                }
                            }
                            if(!isRulesCategory(productCategory)) {
                                leafCache.add(productCategory.getItemDisplayName());
                            }
                        }
                    }
                    if(leafCache.size() > 0) {
                        for(String leaf : leafCache) {
                            sku.addCategoryLeaf(leaf);
                        }
                    }
                    
                    Set<String> nodeCache = new HashSet<String>();
                    
                    for(String token : tokenCache){
                        String[] splitToken = token.split("\\.");
                        if(splitToken != null && splitToken.length > 2) {
                            List<String> tokenList = Arrays.asList(splitToken);
                            tokenList = tokenList.subList(2, tokenList.size());
                            if (!tokenList.isEmpty()) {
                                nodeCache.addAll(tokenList);
                            }
                        }
                    }
                    
                    if(nodeCache.size() > 0) {
                        nodeCache.removeAll(leafCache);
                        for(String node : nodeCache) {
                            sku.addCategoryNode(node);
                        }
                    }
                }
            } catch (Exception ex) {
                if (isLoggingError()) {
                    logError("Problem generating the categoryids attribute", ex);
                }
            }
        }
        stopTimer("processProduct.sku.loadCategoryPaths");
    }

    private boolean isRulesCategory(RepositoryItem category) throws RepositoryException {
    	if (category == null) {
    		return false;
    	}
    	return RuleBasedCategoryProperty.ITEM_DESCRIPTOR.equals(category.getItemDescriptor().getItemDescriptorName());
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
     * @param sku
     *            The product to set the attributes to.
     * @param category
     *            The repositoryItem of the current level
     * @param hierarchyCategories
     *            The list where we store the categories during the recursion
     * @param catalogAssignments
     *            The list of catalogs to restrict the category token generation
     */
    private void loadCategoryPathsAndAncestorIds(Sku sku, RepositoryItem category,
            List<RepositoryItem> hierarchyCategories, Set<RepositoryItem> catalogAssignments, Set<String> tokenCache,
            Set<String> ancestorCache) {
        Set<RepositoryItem> parentCategories = (Set<RepositoryItem>) category.getPropertyValue("fixedParentCategories");

        if (parentCategories != null && parentCategories.size() > 0) {
            hierarchyCategories.add(0, category);
            for (RepositoryItem parentCategory : parentCategories) {
                loadCategoryPathsAndAncestorIds(sku, parentCategory, hierarchyCategories, catalogAssignments, tokenCache, ancestorCache);
            }
            hierarchyCategories.remove(0);
        } else {
            Set<RepositoryItem> catalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs");
            for(RepositoryItem catalog : catalogs){
                if(catalogAssignments.contains(catalog)){
                    generateCategoryTokens(sku, hierarchyCategories, catalog.getRepositoryId(), tokenCache);
                }
            }
        }
        if (!ancestorCache.contains(category.getRepositoryId())) {
            sku.addAncestorCategory(category.getRepositoryId());
            ancestorCache.add(category.getRepositoryId());
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
     * @param sku
     *            The document to set the attributes to.
     * @param hierarchyCategories
     *
     * @param catalog
     *            
     */
    private void generateCategoryTokens(Sku sku, List<RepositoryItem> hierarchyCategories,
            String catalog, Set<String> tokenCache) {
        if (hierarchyCategories == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder builderIds = new StringBuilder();
        for (int i = 0; i <= hierarchyCategories.size(); i++) {
            builder.append(i).append(".").append(catalog).append(".");
            builderIds.append(catalog).append(".");
            
            for (int j = 0; j < i; j++) {
                builder.append(hierarchyCategories.get(j).getItemDisplayName()).append(".");
                builderIds.append(hierarchyCategories.get(j).getRepositoryId()).append(".");
            }
            builder.setLength(builder.length() - 1);
            builderIds.setLength(builderIds.length() - 1);

            String token = builder.toString();
            if (!tokenCache.contains(token)) {
                sku.addCategoryToken(builder.toString());
                sku.addCategoryPath(builderIds.toString());
                tokenCache.add(token);
            }
            builder.setLength(0);
            builderIds.setLength(0);
        }
    }
}
