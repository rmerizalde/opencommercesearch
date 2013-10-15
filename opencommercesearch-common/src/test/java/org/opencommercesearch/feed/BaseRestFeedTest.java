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
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.opencommercesearch.api.ProductService;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.data.Status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class BaseRestFeedTest {

    @Mock
    private Repository repository;

    @Mock
    private RepositoryView repositoryView;

    @Mock
    private RqlStatement rqlCount;

    @Mock
    private RqlStatement rql;

    @Mock
    private RepositoryItem itemA;

    @Mock
    private RepositoryItem itemB;

    @Mock
    private ProductService productService;

    @Spy
    private BaseRestFeed feed = new BaseRestFeed() {

        @Override
        public ProductService.Endpoint getEndpoint() {
            return ProductService.Endpoint.BRANDS;
        }

        @Override
        protected JSONObject repositoryItemToJson(RepositoryItem item) throws JSONException, RepositoryException {
            return new JSONObject().put("id", item.getRepositoryId());
        }

        @Override
        protected String[] getRequiredItemFields() {
            return new String[0];
        }
    };

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        when(productService.getUrl4Endpoint(ProductService.Endpoint.BRANDS)).thenReturn("http://localhost:9000/v1/brands");
        when(productService.getUrl4Endpoint(ProductService.Endpoint.BRANDS, "commit")).thenReturn("http://localhost:9000/v1/brands/commit");
        when(productService.getUrl4Endpoint(ProductService.Endpoint.BRANDS, "rollback")).thenReturn("http://localhost:9000/v1/brands/rollback");

        feed.setRepository(repository);
        feed.setCountRql(rqlCount);
        feed.setRql(rql);
        feed.setItemDescriptorName("TestDescriptor");
        feed.setEnabled(true);
        feed.setProductService(productService);
        feed.doStartService();
        feed.setErrorThreshold(0.5);
        feed.setBatchSize(2);

        when(repository.getView("TestDescriptor")).thenReturn(repositoryView);

        when(itemA.getRepositoryId()).thenReturn("itemA");
        when(itemB.getRepositoryId()).thenReturn("itemB");
    }

    @Test
    public void testIndexNoItems() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(0);

        Response response = new Response(new Request());
        response.setStatus(Status.SUCCESS_OK);
        when(productService.handle(any(Request.class))).thenReturn(response);

        feed.startFeed();

        verify(productService, never()).handle(argument.capture());
    }

    @Test
    public void testIndexItems() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(2);
        when(rql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenReturn(new RepositoryItem[]{itemA, itemB}).thenReturn(null);

        Response responseOk = new Response(new Request());
        responseOk.setStatus(Status.SUCCESS_OK);

        Response responseCreated = new Response(new Request());
        responseCreated.setStatus(Status.SUCCESS_CREATED);
        when(productService.handle(any(Request.class))).thenReturn(responseOk).thenReturn(responseCreated).thenReturn(responseOk);

        feed.startFeed();

        verify(productService, times(3)).handle(argument.capture());

        GZIPInputStream is = new GZIPInputStream(argument.getAllValues().get(1).getEntity().getStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String jsonString = br.readLine();
        assertNotNull(jsonString);

        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray items = (JSONArray) jsonObject.get("brands");

        assertEquals(((JSONObject)items.get(0)).get("id"), "itemA");
        assertEquals(((JSONObject)items.get(1)).get("id"), "itemB");

        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/brands/commit", argument.getValue().getResourceRef().toString());
    }

    @Test
    public void testIndexItemsRollback() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(4);
        when(rql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenThrow(new RepositoryException());

        Response responseOk = new Response(new Request());
        responseOk.setStatus(Status.SUCCESS_OK);

        Response responseCreated = new Response(new Request());
        responseCreated.setStatus(Status.SUCCESS_OK);
        when(productService.handle(any(Request.class))).thenReturn(responseOk).thenReturn(responseCreated).thenReturn(responseOk);

        feed.startFeed();

        verify(productService, times(2)).handle(argument.capture());

        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/brands/rollback", argument.getValue().getResourceRef().toString());
    }

    @Test
    public void testErrorThreshold() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);

        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(4);
        when(rql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenReturn(new RepositoryItem[]{itemA, itemB}).thenReturn(new RepositoryItem[]{itemA, itemB}).thenReturn(null);

        Response badResponse = new Response(new Request());
        badResponse.setStatus(Status.SERVER_ERROR_INTERNAL);

        Response goodResponse = new Response(new Request());
        goodResponse.setStatus(Status.SUCCESS_OK);
        when(productService.handle(any(Request.class))).thenReturn(goodResponse).thenReturn(badResponse).thenReturn(goodResponse);

        feed.startFeed();

        verify(productService, times(3)).handle(argument.capture());
        verify(rql, times(1)).executeQueryUncached(eq(repositoryView), (Object[]) anyObject());

        GZIPInputStream is = new GZIPInputStream(argument.getAllValues().get(1).getEntity().getStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String jsonString = br.readLine();
        assertNotNull(jsonString);

        //Check that it tried to send the first two items before failing
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray items = (JSONArray) jsonObject.get("brands");

        assertEquals(((JSONObject)items.get(0)).get("id"), "itemA");
        assertEquals(((JSONObject)items.get(1)).get("id"), "itemB");

        //Check that it did rollback afterwards
        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/brands/rollback", argument.getValue().getResourceRef().toString());
    }
}
