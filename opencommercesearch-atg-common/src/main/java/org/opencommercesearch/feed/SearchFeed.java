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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.SearchServerException;
import org.opencommercesearch.api.ProductService;
import org.opencommercesearch.client.Product;
import org.opencommercesearch.client.ProductList;
import org.opencommercesearch.client.impl.Sku;
import org.opencommercesearch.service.localeservice.FeedLocaleService;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.*;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.StreamRepresentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opencommercesearch.Utils.errorMessage;
import static org.opencommercesearch.api.ProductService.Endpoint;

/**
 * This class provides a basic functionality to generate a search feed. This includes:
 *  - Product loading
 *  - Category tokens
 *
 * TODO implement default feed functionality
 */
@SuppressWarnings("unchecked")
public abstract class SearchFeed extends GenericService {
    public enum FeedType {
        FULL_FEED,
        INCREMENTAL_FEED,
        MANUAL_FEED
    }

    private static SendQueueItem POISON_PILL = new SendQueueItem();

    private Repository productRepository;
    private String productItemDescriptorName;
    private RqlStatement productCountRql;
    private RqlStatement productRql;
    private int productBatchSize;
    private int indexBatchSize;
    private ProductService productService;
    private ObjectMapper mapper;
    private String endpointUrl;
    private int workerCount;
    private ExecutorService productTaskExecutor;
    private AtomicInteger processedProductCount;

    /**
     * Counter of product index failures.
     */
    private AtomicInteger failedProductCount;
    private AtomicInteger indexedProductCount;
    private ExecutorService sendTaskExecutor;
    private BlockingDeque<SendQueueItem> sendQueue;
    private volatile boolean running;
    private FeedLocaleService localeService;

    /**
     * Max error percentage tolerated by this feed. If this threshold is reached, then the feed will be discarded
     * since it will be considered risky. I.e. set it to 0.1 if you want a maximum of 10% errors of the total items
     * cause the feed to stop.
     */
    private double errorThreshold;

    /**
     * The error threshold for the current run.
     */
    private int currentErrorThreshold;

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


    public ProductService getProductService() {
        return productService;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
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

    public int getCurrentProcessedProductCount() {
        return processedProductCount.get();
    }

    public int getCurrentIndexedProductCount() {
        return indexedProductCount.get();
    }

    public int getCurrentSendQueueSize() {
        return sendQueue.size();
    }

    public int getCurrentFailedProductCount() {
        return failedProductCount.get();
    }

    public FeedLocaleService getLocaleService() {
        return localeService;
    }

    public void setLocaleService(FeedLocaleService localeService) {
        this.localeService = localeService;
    }

    public double getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(double errorThreshold) {
        this.errorThreshold = errorThreshold;
    }

    public int getCurrentErrorThreshold() {
        return currentErrorThreshold;
    }

    public void setCurrentErrorThreshold(int currentErrorThreshold) {
        this.currentErrorThreshold = currentErrorThreshold;
    }

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        endpointUrl = getProductService().getUrl4Endpoint(Endpoint.PRODUCTS);
        mapper = new ObjectMapper()
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (getWorkerCount() <= 0) {
            if (isLoggingInfo()) {
                logInfo("At least one worker is required to process the feed, setting number of workers to 1");
                setWorkerCount(1);
            }
        }
        productTaskExecutor = Executors.newFixedThreadPool(getWorkerCount());
        processedProductCount = new AtomicInteger(0);
        indexedProductCount = new AtomicInteger(0);
        failedProductCount = new AtomicInteger(0);
        sendTaskExecutor = Executors.newSingleThreadExecutor();
        sendQueue = new LinkedBlockingDeque<SendQueueItem>();
    }

    @Override
    public void doStopService() throws ServiceException {
        terminate();
        productTaskExecutor.shutdown();
        sendTaskExecutor.shutdown();
    }

    /**
     * Check if a full feed is running
     * @return true if the full feed is running. Otherwise return false
     */
    public boolean isFeedRunning() {
        return running;
    }

    public void terminate() {
        running = false;
    }

    public static class FeedSku extends Sku {

        @JsonIgnore
        private int customSort;

        @JsonIgnore
        private boolean isAssigned;

        public int getCustomSort() {
            return customSort;
        }

        public void setCustomSort(int customSort) {
          this.customSort = customSort;
        }

        @JsonIgnore
        public boolean isAssigned() {
            return isAssigned;
        }

        public void setAssigned(boolean isAssigned) {
           this.isAssigned = isAssigned;
       }
    }

    /**
     * A task to process the products in a product catalog partition.
     */
    private class ProductPartitionTask implements Runnable {
        private CountDownLatch endGate;
        private int offset;
        private int limit;
        private FeedType type;
        private long feedTimestamp;
        private String name;

        ProductPartitionTask(int offset, int limit, FeedType type, long feedTimestamp, CountDownLatch endGate) {
            this.offset = offset;
            this.limit = limit;
            this.type = type;
            this.feedTimestamp = feedTimestamp;
            this.endGate = endGate;
            this.name = offset + " - " + (offset + limit);
        }

        public String getName() {
            return name;
        }

        public void run() {
            int localProductProcessedCount = 0;

            try {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Started processing partition " + getName());
                }

                Integer[] rqlArgs = new Integer[] { offset, getProductBatchSize() };
                RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
                RepositoryItem[] productItems = productRql.executeQueryUncached(productView, rqlArgs);
                SearchFeedProducts products = new SearchFeedProducts();
                int productCount = limit;
                boolean done = false;
                boolean shouldStop = false;

                while (running && !done && productItems != null) {
                    for (RepositoryItem product : productItems) {
                        if (isProductIndexable(product)) {
                            processProduct(product, products);
                            indexedProductCount.incrementAndGet();
                            sendProducts(products, type, feedTimestamp, getIndexBatchSize(), true);
                        }
                        processedProductCount.incrementAndGet();
                        localProductProcessedCount++;

                        //If global failures exceed the error threshold, then stop this partition task
                        shouldStop = failedProductCount.get() >= currentErrorThreshold;
                        done = localProductProcessedCount >= limit || shouldStop;
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

                if(!shouldStop) {
                    sendProducts(products, type, feedTimestamp, 0, true);
                }
            } catch (Exception ex) {
                if (isLoggingError()) {
                    logError("Exception processing catalog partition: " + getName(), ex);
                }
            } finally {
                if (isLoggingInfo()) {
                    logInfo(Thread.currentThread() + " - Finished processing partition " + getName());
                }

                int unprocessedProductCount = limit - localProductProcessedCount;
                failedProductCount.addAndGet(unprocessedProductCount);
                endGate.countDown();
            }
        }
    }

    private static class SendQueueItem {
        Locale locale;
        FeedType type;
        long feedTimestamp;
        ProductList productList;

        SendQueueItem() {}

        SendQueueItem(Locale locale, FeedType type, long feedTimestamp, ProductList productList) {
            this.locale = locale;
            this.type = type;
            this.feedTimestamp = feedTimestamp;
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

                    int count = item.productList.getProducts().size();
                    if(!sendProducts(item.locale, item.type, item.feedTimestamp, item.productList)) {
                        failedProductCount.addAndGet(count);
                    }
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
     * Sends the products for indexing.
     * <p/>
     * If async this method always returns zero. If not async, this method will return the number of successfully sent products.
     *
     * @param products the lists of products to be indexed
     * @param type is the feed's type
     * @param feedTimestamp the feed's timestamp
     * @param min the minimum size of of a product list. If the size is not met then the products are not sent
     *            for indexing
     * @param async determines if the products should be send right away or asynchronously.
     * return If async, always zero. If not async, the number of successfully sent products.
     */
    public int sendProducts(SearchFeedProducts products, FeedType type, long feedTimestamp, int min, boolean async) {
        int sent = 0;
        for (Locale locale : products.getLocales()) {
            List<Product> productList = products.getProducts(locale);

            if (products.getSkuCount(locale) > min) {
                try {
                    if (async) {
                        List<Product> clone = new ArrayList<Product>(productList.size());
                        clone.addAll(productList);
                        sendQueue.offer(new SendQueueItem(locale, type, feedTimestamp, new ProductList(clone, feedTimestamp)));
                        //If an async call is made, this always return true.
                        return 0;
                    } else {
                        int count = productList.size();
                        if(!sendProducts(locale, type, feedTimestamp, new ProductList(productList, feedTimestamp))) {
                            failedProductCount.addAndGet(count);
                            return sent;
                        }
                        else {
                            sent += count;
                        }
                    }
                } finally {
                    productList.clear();
                }
            }
            else {
                sent += productList.size();
            }
        }

        return sent;
    }

    /**
     * Helper method that actually sends the products for indexing
     * @return False if there are errors sending the product, true otherwise.
     */
    private boolean sendProducts(final Locale locale, final FeedType type, final long feedTimestamp, final ProductList productList) {
        try {
            final StreamRepresentation representation = new StreamRepresentation(MediaType.APPLICATION_JSON) {
                @Override
                public InputStream getStream() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(OutputStream outputStream) throws IOException {
                    try {
                        getObjectMapper().writeValue(outputStream, productList);
                    } catch (IOException ex) {
                        if (isLoggingDebug()) {
                            logDebug("Unable to convert product list to JSON");
                        }
                     }
                }
            };
            final Request request = new Request(Method.PUT, endpointUrl, new EncodeRepresentation(Encoding.GZIP, representation));
            final ClientInfo clientInfo = request.getClientInfo();
            clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(locale.getLanguage()))));
            Response response = null;

            try {
                response = getProductService().handle(request);

                if (!response.getStatus().equals(Status.SUCCESS_CREATED)) {
                    if (isLoggingInfo()) {
                        logInfo("Sending products [" + productsId(productList.getProducts()) + "] fail with status: " + response.getStatus() + " ["
                                + errorMessage(response.getEntity()) + "]");
                    }
                    onProductsSentError(type, feedTimestamp, locale, productList.getProducts(), response);
                    return false;
                } else {
                    onProductsSent(type, feedTimestamp, locale, productList.getProducts(), response);
                    return true;

                }
            } finally {
                if (response != null) {
                    response.release();
                }
                request.release();
            }
       }
        catch (Exception ex) {
            if (isLoggingInfo()) {
                logInfo("Sending products [" + productsId(productList.getProducts()) + "] failed with unexpected exception", ex);
            }
            onProductsSentError(type, feedTimestamp, locale, productList.getProducts(), ex);
            return false;
        }
        finally {
            productList.getProducts().clear();
        }
    }

    /**
     * Deletes the product with the given id from index
     *
     * @param id is the id of the product to be deleted
     * @param feedTimestamp is the feed's timestamp
     */
    public void delete(String id, long feedTimestamp, String from) {
        Set<String> languages = new HashSet<String>();

        for (Locale locale : localeService.getSupportedLocales()) {
            if (!languages.contains(locale.getLanguage())) {
                delete(id, feedTimestamp, locale, from);
                languages.add(locale.getLanguage());
            }
        }
    }

    protected void delete(String id, long feedTimestamp, Locale locale, String from) {
        try {
            final String endpointUrl = urlWithParameters(getProductService().getUrl4Endpoint(Endpoint.PRODUCTS, id)) + "feedTimestamp=" + feedTimestamp + "&from="+from;
            final Request request = new Request(Method.DELETE, endpointUrl);
            final ClientInfo clientInfo = request.getClientInfo();
            clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(locale.getLanguage()))));
            Response response = null;

            try {
                response = getProductService().handle(request);

                if (isLoggingInfo()) {
                    if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                        logInfo("Successfully deleted product " + id + " for " + locale.getLanguage() + " with feed timestamp " + feedTimestamp);
                    } else {
                        logInfo("Deleting product " + id + " for " + locale.getLanguage() + " failed with status: " + response.getStatus() + " with feed timestamp " + feedTimestamp);
                    }
                }
            } finally {
                if (response != null) {
                    response.release();
                }
                request.release();
            }
        } catch (Exception ex) {
            if (isLoggingError()) {
                logError("Deleting product " + id + " failed", ex);
            }
        }
    }

    public void delete(long feedTimestamp) {
        Set<String> languages = new HashSet<String>();
        String endpointUrl = urlWithParameters(getProductService().getUrl4Endpoint(Endpoint.PRODUCTS));

        for (Locale locale : localeService.getSupportedLocales()) {
            if (!languages.contains(locale.getLanguage())) {
                try {
                    endpointUrl += "feedTimestamp=" + feedTimestamp;
                    final Request request = new Request(Method.DELETE, endpointUrl);
                    final ClientInfo clientInfo = request.getClientInfo();
                    clientInfo.setAcceptedLanguages(Arrays.asList(new Preference<Language>(new Language(locale.getLanguage()))));
                    Response response = null;

                    try {
                        response = getProductService().handle(request);

                        if (isLoggingInfo()) {
                            if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                                logInfo("Successfully deleted products for " + locale.getLanguage() + " with feed timestamp before to "
                                        + feedTimestamp);
                            } else {
                                logInfo("Deleting products for " + locale.getLanguage() + " with feed timestamp before to "
                                        + feedTimestamp + " failed with status: " + response.getStatus());
                            }
                        }
                        languages.add(locale.getLanguage());
                    } finally {
                        if (response != null) {
                            response.release();
                        }
                        if (request != null) {
                            request.release();
                        }
                    }
                } catch (Exception ex) {
                    if (isLoggingError()) {
                        logError("Deleting products for " + locale.getLanguage() + " with feed timestamp before to "
                                + feedTimestamp + " failed", ex);
                    }
                }
            }
        }
    }

    /**
     * Helper method to format an endpoint Url
     */
    private String urlWithParameters(String url) {
        if (url.indexOf('?') != -1) {
            return url + '&';
        } else {
            return url + '?';
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
            running = true;
            final long startTime = System.currentTimeMillis();

            RepositoryView productView = getProductRepository().getView(getProductItemDescriptorName());
            int productCount = productCountRql.executeCountQuery(productView, null);
            currentErrorThreshold = (int) (productCount * getErrorThreshold());

            //If error threshold is too small, don't do anything with it.
            if(currentErrorThreshold == 0) {
                currentErrorThreshold = productCount;
            }

            if (isLoggingInfo()) {
                logInfo("Started full feed for " + productCount + " products");
            }

            final FeedType type = FeedType.FULL_FEED;
            final long feedTimestamp = System.currentTimeMillis();

            onFeedStarted(type, feedTimestamp);
            processedProductCount.set(0);
            indexedProductCount.set(0);
            failedProductCount.set(0);

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

                productTaskExecutor.execute(new ProductPartitionTask(offset, limit, type, feedTimestamp, endGate));
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
                if(failedProductCount.get() < currentErrorThreshold) {
                    delete(feedTimestamp);
                    onFeedFinished(type, feedTimestamp);

                    if (isLoggingInfo()) {
                        logInfo("Full feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, "
                                + indexedProductCount.get() + " products were indexable from  " + processedProductCount.get()
                                + " processed products");
                    }
                }
                else {
                    onFeedFailed(type, feedTimestamp);
                    logError("Full feed interrupted since it seems to be failing too often. At least " + (getErrorThreshold() * 100) + "% out of " + productCount + " items had errors");
                }
            }
            else {
                if (isLoggingInfo()) {
                    logInfo("Full feed was terminated");
                }
            }
        } finally {
            running = false;
        }
    }

    protected String productsId(List<Product> products) {
        if (products == null || products.size() == 0) {
            return StringUtils.EMPTY;
        }
        StringBuilder buffer = new StringBuilder();

        for (Product product : products) {
            buffer.append(product.getId()).append(", ");
        }
        buffer.setLength(buffer.length() - 2);
        return buffer.toString();
    }

    /**
     * Fires an event when a feed is started
     *
     * @param type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     */
    protected abstract void onFeedStarted(FeedType type, long feedTimestamp);

    /**
     * Fires an event when products are sent successfully for indexing
     *
     * @param type type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     * @param locale is the products locale
     * @param productList is the indexed product list
     * @param response is the response from the API
     */
    protected abstract void onProductsSent(FeedType type,  long feedTimestamp, Locale locale, List<Product> productList, Response response);

    /**
     * Fires an event when product indexing fails
     *
     * @param type type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     * @param locale is the products locale
     * @param productList is the product list that couldn't be indexed
     * @param ex is the exception that prevented to products from been indexed
     */
    protected abstract void onProductsSentError(FeedType type, long feedTimestamp, Locale locale, List<Product> productList, Exception ex);

    /**
     * Fires an event when product indexing fails
     *
     * @param type type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     * @param locale is the products locale
     * @param productList is the product list that couldn't be indexed
     * @param response is the response from the API
     */
    protected abstract void onProductsSentError(FeedType type, long feedTimestamp, Locale locale, List<Product> productList, Response response);

    /**
     * Fires an event when a feed finishes
     *
     * @param type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     */
    protected abstract void onFeedFinished(FeedType type, long feedTimestamp);

    /**
     * Fires an event when a feed is cancelled due to errors
     *
     * @todo: make this method abstract
     *
     * @param type is the feed's type
     * @param feedTimestamp is the feed's timestamp
     */
    protected void onFeedFailed(FeedType type, long feedTimestamp) {
    }

    protected abstract void processProduct(RepositoryItem product, SearchFeedProducts products)
            throws RepositoryException, InventoryException;

    /**
     * Loads the categories the sku is assigned to
     *
     * @param sku
     *            The document to set the attributes to.
     * @param product
     *            The RepositoryItem for the product item descriptor
     * @param skuCatalogAssignments
     *            If the product is belongs to a category in any of those
     *            catalogs then that category is part of the returned value.
     */
    public void checkSkuAssigned(FeedSku sku, RepositoryItem product,
                Set<RepositoryItem> skuCatalogAssignments) {
        if (product != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<RepositoryItem> productCategories = (Set<RepositoryItem>) product
                        .getPropertyValue("parentCategories");
                if (productCategories != null) {
                    for (RepositoryItem productCategory : productCategories) {
                        if (isCategoryInCatalogs(productCategory, skuCatalogAssignments)) {
                            if (isCategoryIndexable(productCategory)) {
                                checkAssigned(sku, productCategory, skuCatalogAssignments);
                            }
                        }
                        if (sku.isAssigned()) {
                            break;
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
     * Helper method to mark an sku as assigned.
     *
     * @param sku
     *            The product to set the attributes to.
     * @param category
     *            The repositoryItem of the current level
     * @param catalogAssignments
     *            The list of catalogs to restrict the category token generation
     */
    private void checkAssigned(FeedSku sku, RepositoryItem category, Set<RepositoryItem> catalogAssignments) {
        Set<RepositoryItem> parentCategories = (Set<RepositoryItem>) category.getPropertyValue("fixedParentCategories");

        if (parentCategories != null && parentCategories.size() > 0) {
            for (RepositoryItem parentCategory : parentCategories) {
                if (sku.isAssigned()) {
                    break;
                }
                checkAssigned(sku, parentCategory, catalogAssignments);
            }
        } else {
            Set<RepositoryItem> catalogs = (Set<RepositoryItem>) category.getPropertyValue("catalogs");
            for(RepositoryItem catalog : catalogs) {
                if(catalogAssignments.contains(catalog)){
                    sku.setAssigned(true);
                    break;
                }
            }
        }
    }

    /**
     * Helper method to test if category is assigned to any of the given catalogs
     * 
     * @param category
     *            the category to be tested
     * @param catalogs
     *            the set of categories to search in
     * @return true if category is assigned to a least of of the given catalogs
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
}
