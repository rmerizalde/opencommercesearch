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
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;

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
    private Client client;

    @Spy
    private BaseRestFeed feed = new BaseRestFeed() {
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

        feed.setRepository(repository);
        feed.setCountRql(rqlCount);
        feed.setRql(rql);
        feed.setClient(client);
        feed.setItemDescriptorName("TestDescriptor");
        feed.setEnabled(true);
        when(repository.getView("TestDescriptor")).thenReturn(repositoryView);

        when(itemA.getRepositoryId()).thenReturn("itemA");
        when(itemB.getRepositoryId()).thenReturn("itemB");
    }

    @Test
    public void testIndexNoItems() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(0);

        feed.startFeed();

        verify(client, times(2)).handle(argument.capture(), (Response) anyObject());
        assertEquals(Method.DELETE, argument.getAllValues().get(0).getMethod());
        assertEquals("http://localhost:9000/v1/items?query=*:*", argument.getAllValues().get(0).getResourceRef().toString());

        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/items?commit=true", argument.getValue().getResourceRef().toString());
    }

    @Test
    public void testIndexItems() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(2);
        when(rql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenReturn(new RepositoryItem[]{itemA, itemB}).thenReturn(null);

        feed.startFeed();

        verify(client, times(3)).handle(argument.capture(), (Response) anyObject());

        String jsonString = argument.getAllValues().get(1).getEntity().getText();
        assertNotNull(jsonString);

        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray items = (JSONArray) jsonObject.get("items");

        assertEquals(((JSONObject)items.get(0)).get("id"), "itemA");
        assertEquals(((JSONObject)items.get(1)).get("id"), "itemB");

        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/items?commit=true", argument.getValue().getResourceRef().toString());
    }

    @Test
    public void testIndexItemsRollback() throws Exception {
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        when(rqlCount.executeCountQuery(repositoryView, null)).thenReturn(4);
        when(rql.executeQueryUncached(eq(repositoryView), (Object[]) anyObject())).thenThrow(new RepositoryException());

        feed.startFeed();

        verify(client, times(2)).handle(argument.capture(), (Response) anyObject());

        assertEquals(Method.POST, argument.getValue().getMethod());
        assertEquals("http://localhost:9000/v1/items?rollback=true", argument.getValue().getResourceRef().toString());
    }
}
