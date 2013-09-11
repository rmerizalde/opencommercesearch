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
import org.opencommercesearch.SearchServerException;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StreamRepresentation;
import org.restlet.representation.StringRepresentation;

import java.io.IOException;
import java.util.Arrays;

/**
 * Base feed class that sends repository items to the OCS rest API.
 * <p/>
 * Data is transferred in JSON format and through regular HTTP calls. Child classes should handle
 * repository item to JSON transforms.
 * <p/>
 * Notice that the API must be aware of the repository item being fed (checkout opencommercesearch-api project).
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
     * Restlet client to handle all HTTP communications.
     */
    private Client client;

    /**
     * Whether or not this feed is enabled.
     */
    private boolean enabled;


    //TODO: Remove this two properties and use Settings.java


    /**
     * Whether or not the feed should send items to the preview collection (if false, everything is sent to the public collection)
     */
    private boolean isPreview = false;

    /**
     * Endpoint URL to post data to.
     */
    private String endpointUrl = "http://localhost:9000/v1/items";

    @Override
    public void doStartService() throws ServiceException {
        super.doStartService();
        client = new Client(Protocol.HTTP);
    }

    @Override
    public void doStopService() throws ServiceException {
        super.doStopService();
        try {
            client.stop();
        } catch (Exception ex) {
            throw new ServiceException("Error stopping restlet client", ex);
        } finally {
            client = null;
        }
    }

    /**
     * Start running this feed.
     * @throws RepositoryException If there are problems reading the repository items from the database.
     */
    public void startFeed() throws RepositoryException, SearchServerException {
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

            sendDeleteByQuery();

            if(count > 0) {
                Integer[] rqlArgs = new Integer[] { 0, getBatchSize() };
                RepositoryItem[] items = rql.executeQueryUncached(itemView, rqlArgs);

                while (items != null) {
                    try {
                        int sent = sendItems(items);
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

            sendCommit();
        }
        catch(Exception e) {
            sendRollback();
        }

        if (isLoggingInfo()) {
            logInfo(itemDescriptorName + " feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, " +
                    processed + " processed items and " + failed + " failures.");
        }
    }

    /**
     * Convert the given items to a JSON list and post them to the given API endpoint.
     * @param itemList The list of repository items to be sent.
     * @return The total count of items sent.
     * @throws RepositoryException if item data from the list can't be read.
     */
    private int sendItems(RepositoryItem[] itemList) throws RepositoryException{
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
            obj.put(getEndPointName(), jsonObjects);

            //TODO (Javier Mendez): Use custom StreamRepresentation to write the JSON directly to the HTTP output stream
            final StringRepresentation jsonRepresentation = new StringRepresentation(obj.toString(), MediaType.APPLICATION_JSON);
            String url = endpointUrl;

            if (isPreview) {
                url += "?preview=true";
            }

            final Request request = new Request(Method.PUT, url, jsonRepresentation);
            final Response response = client.handle(request);

            if (!response.getStatus().equals(Status.SUCCESS_CREATED)) {
                if (isLoggingInfo()) {
                    logInfo("Sending " + itemDescriptorName + "[" + getIdsFromItemsArray(itemList) + "] failed with status: " + response.getStatus() + " ["
                            + errorResponseToString(response.getEntity()) + "]");
                }

                return 0;
            }

            return sent;
        }
        catch (JSONException ex) {
            if (isLoggingInfo()) {
                logInfo("Cannot create JSON representation for " + itemDescriptorName + " info [" + getIdsFromItemsArray(itemList) + "]");
            }

            return 0;
        }
    }

    /**
     * Sends a commit request to the API.
     * @throws SearchServerException If the commit fails.
     */
    private void sendCommit() throws SearchServerException {
        final Request request = new Request(Method.POST, endpointUrl + "?commit=true");
        final Response response = client.handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw SearchServerException.create(SearchServerException.Code.COMMIT_EXCEPTION, new Exception("Failed to send commit with status " + response.getStatus() + errorResponseToString(response.getEntity())));
        }
    }

    /**
     * Sends a rollback request to the API.
     * @throws SearchServerException If the rollback fails.
     */
    private void sendRollback() throws SearchServerException {
        final Request request = new Request(Method.POST, endpointUrl + "?rollback=true");
        final Response response = client.handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw SearchServerException.create(SearchServerException.Code.COMMIT_EXCEPTION, new Exception("Failed to send rollback with status " + response.getStatus() + errorResponseToString(response.getEntity())));
        }
    }

    /**
     * Sends a delete by query request to the API.
     * @throws SearchServerException If the delete fails.
     */
    private void sendDeleteByQuery() throws SearchServerException {
        final Request request = new Request(Method.DELETE, endpointUrl + "?query=*:*");
        final Response response = client.handle(request);

        if (!response.getStatus().equals(Status.SUCCESS_OK)) {
            throw SearchServerException.create(SearchServerException.Code.UPDATE_EXCEPTION, new Exception("Failed to send delete by query with status " + response.getStatus() + errorResponseToString(response.getEntity())));
        }
    }

    /**
     * Gets the name on the endpoint URL. For example, from 'http://localhost:9000/v1/items' it returns 'items'.
     */
    private String getEndPointName() {
        if(endpointUrl.charAt(endpointUrl.length() - 1) == '/') {
            String noForwardSlash = endpointUrl.substring(0, endpointUrl.length() - 1);
            return noForwardSlash.substring(noForwardSlash.lastIndexOf('/') + 1);
        }
        else {
            return endpointUrl.substring(endpointUrl.lastIndexOf('/') + 1);
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
            if(representation != null) {
                JSONObject obj = new JSONObject(representation.getText());
                message = obj.getString("message");
            }
        }
        catch (JSONException ex) {
            // do nothing
        }
        catch (IOException ex) {
            // do nothing
        }

        return message;
    }

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

    public boolean isPreview() {
        return isPreview;
    }

    public void setPreview(boolean isPreview) {
        this.isPreview = isPreview;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
