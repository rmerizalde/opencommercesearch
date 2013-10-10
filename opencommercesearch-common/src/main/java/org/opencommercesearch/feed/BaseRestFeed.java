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

import atg.json.JSONArray;
import atg.json.JSONException;
import atg.json.JSONObject;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.api.ProductService;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StreamRepresentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import static org.opencommercesearch.api.ProductService.Endpoint;

/**
 * Base feed class that sends repository items to the OCS REST API.
 * <p/>
 * Data is transferred in JSON format and through regular HTTP calls. Child classes should handle
 * repository item to JSON transforms.
 * <p/>
 * Notice that the API must be aware of the repository item being fed (checkout opencommercesearch-api project).
 * <p/>
 * A feed can be transactional or not. When using transactions, all items will be cleared first. If an exception happens
 * all changes will be rolled back. If not, changes are committed. Be aware when using Solr. Solr transactions are not isolated.
 * Make sure the SolrCore doesn't have auto commits enabled.
 *
 * Non transactional feeds will delete all items that were not updated during the feed.
 *
 * @author jmendez
 */
public abstract class BaseRestFeed extends GenericService {

    /**
     * Repository to query for items
     */
    private Repository repository;

    /**
     * The actual item descriptor name being fed.
     */
    private String itemDescriptorName;

    /**
     * RQL query to get total items to be fed
     */
    private RqlStatement countRql;

    /**
     * Actual RQL that will get all the items to be fed (and fields for each item)
     */
    private RqlStatement rql;

    /**
     * Number of items that will be sent on each request
     */
    private int batchSize;

    /**
     * Whether or not this feed is enabled.
     */
    private boolean enabled;

    /**
     * Transactional
     */
    private boolean transactional = true;

    private ProductService productService;

    private String endpointUrl;

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public String getItemDescriptorName() {
        return itemDescriptorName;
    }

    public void setItemDescriptorName(String itemDescriptorName) {
        this.itemDescriptorName = itemDescriptorName;
    }

    public RqlStatement getCountRql() {
        return countRql;
    }

    public void setCountRql(RqlStatement countRql) {
        this.countRql = countRql;
    }

    public RqlStatement getRql() {
        return rql;
    }

    public void setRql(RqlStatement rql) {
        this.rql = rql;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ProductService getProductService() {
        return productService;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void doStartService() throws ServiceException {
        if (getProductService() == null) {
            throw new ServiceException("No productService found");
        }
        endpointUrl = getProductService().getUrl4Endpoint(getEndpoint());
    }

    /**
     * Start running this feed.
     * @throws RepositoryException If there are problems reading the repository items from the database.
     */
    public void startFeed() throws RepositoryException, IOException {
        if(!isEnabled()) {
            if (isLoggingInfo()) {
                logInfo("Did not start feed for " + itemDescriptorName + " since is disabled. Verify your configuration is correct." );
            }

            return;
        }

        long startTime = System.currentTimeMillis();
        int processed = 0;
        int failed = 0;

        RepositoryView itemView = getRepository().getView(itemDescriptorName);
        int count = countRql.executeCountQuery(itemView, null);

        if (isLoggingInfo()) {
            logInfo("Started " + itemDescriptorName + " feed for " + count + " items." );
        }

        try {
            long feedTimestamp = System.currentTimeMillis();
            if (isTransactional()) {
                sendDeleteByQuery();
            }

            if(count > 0) {
                Integer[] rqlArgs = new Integer[] { 0, getBatchSize() };
                RepositoryItem[] items = rql.executeQueryUncached(itemView, rqlArgs);

                while (items != null) {
                    try {
                        int sent = sendItems(items, feedTimestamp);
                        processed += sent;
                        failed += items.length - sent;
                    }
                    catch (Exception ex) {
                        failed++;
                        if (isLoggingError()) {
                            logError("Cannot send " + itemDescriptorName + "[" + getIdsFromItemsArray(items) + "]", ex);
                        }
                    }

                    rqlArgs[0] += getBatchSize();
                    items = rql.executeQueryUncached(itemView, rqlArgs);

                    if (isLoggingInfo()) {
                        logInfo("Processed " + processed + " " + itemDescriptorName + " items out of " + count + " with " + failed + " failures");
                    }
                }
            }

            if (isTransactional()) {
                sendCommit();
            } else {
                sendDelete(feedTimestamp);
            }
        }
        catch(Exception e) {
            if(isLoggingError()) {
                logError("Error while processing feed.", e);
            }

            if (isTransactional()) {
                sendRollback();
            }
        }

        if (isLoggingInfo()) {
            logInfo(itemDescriptorName + " feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, " +
                    processed + " processed items and " + failed + " failures.");
        }
    }

    /**
     * Convert the given items to a JSON list and post them to the given API endpoint.
     * @param itemList The list of repository items to be sent.
     * @param feedTimestamp is the feed timestamp
     * @return The total count of items sent.
     * @throws RepositoryException if item data from the list can't be read.
     */
    private int sendItems(RepositoryItem[] itemList, long feedTimestamp) throws RepositoryException{
        int sent = 0;

        try {
            final JSONArray jsonObjects = new JSONArray();

            for (RepositoryItem item : itemList) {
                JSONObject json = repositoryItemToJson(item);

                if(json == null) {
                    if (isLoggingDebug()) {
                        logDebug("Sending " + itemDescriptorName + "[" + item.getRepositoryId()
                                + "] failed because it is missing required information. Expected: " + Arrays.toString(getRequiredItemFields()));
                    }
                }
                else {
                    jsonObjects.add(json);
                    sent++;
                }
            }

            final JSONObject obj = new JSONObject();
            obj.put(getEndpoint().getLowerCaseName(), jsonObjects);
            obj.put("feedTimestamp", feedTimestamp);
            final StreamRepresentation representation = new StreamRepresentation(MediaType.APPLICATION_JSON) {
                @Override
                public InputStream getStream() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(OutputStream outputStream) throws IOException {
                    try {
                        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                        obj.write(writer);
                        writer.flush();
                    } catch (JSONException ex) {
                        throw new IOException("Cannot write JSON", ex);
                    }
                }
            };
            final Request request = new Request(Method.PUT, endpointUrl, new EncodeRepresentation(Encoding.GZIP, representation));
            final Response response = getProductService().handle(request);

            try {
                if (!response.getStatus().equals(Status.SUCCESS_CREATED)) {
                    if (isLoggingInfo()) {
                        logInfo("Sending " + itemDescriptorName + "[" + getIdsFromItemsArray(itemList) + "] failed with status: " + response.getStatus() + " ["
                                + errorResponseToString(response.getEntity()) + "]");
                    }

                    return 0;
                }
            } finally {
                if (response != null) {
                    response.release();
                }
                if (request != null) {
                    request.release();
                }
            }

            return sent;
        } catch (JSONException ex) {
            if (isLoggingInfo()) {
                logInfo("Cannot create JSON representation for " + itemDescriptorName + " info [" + getIdsFromItemsArray(itemList) + "]");
            }

            return 0;
        }
    }

    /**
     * Sends a commit request to the API.
     * @throws IOException if the commit fails.
     */
    protected void sendCommit() throws IOException {
        String commitEndpointUrl = productService.getUrl4Endpoint(getEndpoint(), "commit");

        final Request request = new Request(Method.POST, commitEndpointUrl);
        final Response response = getProductService().handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw new IOException("Failed to send commit with status " + response.getStatus() + errorResponseToString(response.getEntity()));
        }
    }

    /**
     * Sends a rollback request to the API.
     * @throws IOException if the rollback fails.
     */
    protected void sendRollback() throws IOException {
        String rollbackEndpointUrl = productService.getUrl4Endpoint(getEndpoint(), "rollback");

        final Request request = new Request(Method.POST, rollbackEndpointUrl);
        final Response response = getProductService().handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw new IOException("Failed to send rollback with status " + response.getStatus() + errorResponseToString(response.getEntity()));
        }
    }

    /**
     * Sends a delete by query request to the API.
     * @throws IOException if the delete fails.
     */
    private void sendDeleteByQuery() throws IOException {
        String deleteEndpointUrl = endpointUrl;
        deleteEndpointUrl += (getProductService().getPreview())? "&" : "?";
        deleteEndpointUrl += "query=*:*";

        final Request request = new Request(Method.DELETE, deleteEndpointUrl);
        final Response response = getProductService().handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw new IOException("Failed to send delete by query with status " + response.getStatus() + errorResponseToString(response.getEntity()));
        }
    }

    public void sendDelete(long feedTimestamp) {
        String deleteEndpointUrl = endpointUrl;
        deleteEndpointUrl += (getProductService().getPreview())? "&" : "?";
        deleteEndpointUrl += "feedTimestamp=" + feedTimestamp;

        final Request request = new Request(Method.DELETE, deleteEndpointUrl);
        final Response response = getProductService().handle(request);

        if (isLoggingInfo()) {
            if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                logInfo("Successfully deleted " + itemDescriptorName + " items with feed timestamp before to " + feedTimestamp);
            } else {
                logInfo("Deleting " + itemDescriptorName + " items with feed timestamp before to " + feedTimestamp + " failed with status: " + response.getStatus());
            }
        }
    }

    /**
     * Get the IDs of all given repository items.
     * @param items Items to get the IDs from.
     * @return List of all IDs concatenated and separated by comma.
     */
    private String getIdsFromItemsArray(RepositoryItem[] items) {
        if (items == null || items.length == 0) {
            return StringUtils.EMPTY;
        }

        StringBuilder buffer = new StringBuilder();

        for (RepositoryItem item : items) {
            buffer.append(item.getRepositoryId()).append(", ");
        }

        //Remove extra comma
        buffer.setLength(buffer.length() - 2);
        return buffer.toString();
    }

    /**
     * Create a printable string out of an HTTP (restlet) error response.
     * @param representation The restlet representation of the error response.
     * @return A printable string containing the HTTP (restlet) error response.
     */
    private String errorResponseToString(Representation representation) {
        String message = "unknown exception";
        try {
            if(representation != null && representation.getText() != null) {
                JSONObject obj = new JSONObject(representation.getText());
                message = obj.getString("message");

                if(isLoggingDebug() && obj.has("detail")) {
                    message += "\n\n" + obj.getString("detail");
                }
            }
        }
        catch (JSONException ex) {
            if(isLoggingError()) {
                logError("Can't parse error response.", ex);
            }
        }
        catch (IOException ex) {
            if(isLoggingError()) {
                logError("Can't get error response.", ex);
            }
        }

        return message;
    }

    /**
     * Return the Endpoint for this feed
     * @return an Endpoint enum representing the endpoint for this feed
     */
    public abstract Endpoint getEndpoint();

    /**
     * Convert the given repository item to its corresponding JSON API format.
     * @param item Repository item to convert.
     * @return The JSON representation of the given repository item, or null if there are missing fields.
     * @throws JSONException if there are format issues when creating the JSON object.
     * @throws RepositoryException if item data from the list can't be read.
     */
    protected abstract JSONObject repositoryItemToJson(RepositoryItem item) throws JSONException, RepositoryException;

    /**
     * Return a list of required fields when transforming a repository item to JSON.
     * <p/>
     * This list is used for logging purposes only.
     * @return List of required fields when transforming a repository item to JSON, required for logging purposes.
     */
    protected abstract String[] getRequiredItemFields();
}

