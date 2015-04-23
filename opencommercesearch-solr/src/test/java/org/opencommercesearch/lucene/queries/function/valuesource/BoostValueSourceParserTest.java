package org.opencommercesearch.lucene.queries.function.valuesource;

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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.RuleManagerParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SolrIndexSearcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opencommercesearch.lucene.queries.function.valuesource.BoostValueSourceParser.BOOST_ID;

/**
 * @author rmerizalde
 */
public class BoostValueSourceParserTest {

    @Mock
    private FunctionQParser fp;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse httpResponse;
    @Mock
    private StatusLine statusLine;
    @Mock
    private HttpEntity httpEntity;
    @Mock
    private SolrParams params;
    @Mock
    private SolrQueryRequest request;
    @Mock
    private IndexSchema schema;
    @Mock
    private FieldType fieldType;
    @Mock
    private ValueSource productIdValueSource;
    @Mock
    private FunctionValues productIdFunctionValues;
    @Mock
    private SolrCache boostCache;
    @Mock
    private SolrIndexSearcher searcher;

    private String boostId = "search_jackes_myCaalog";
    private SchemaField schemaField;
    private BoostValueSourceParser vsp = new BoostValueSourceParser();


    @Before
    public void setup() throws Exception {
        initMocks(this);

        schemaField = new SchemaField("productId", fieldType);

        vsp.defaultClient = httpClient;

        when(fp.getReq()).thenReturn(request);
        when(fp.parseArg()).thenReturn("productId");
        when(request.getParams()).thenReturn(params);
        when(params.get(RuleManagerParams.CATALOG_ID)).thenReturn("myCatalog");
        when(params.get(BOOST_ID)).thenReturn(boostId);
        when(params.get(CommonParams.ROWS)).thenReturn("10");
        when(request.getSchema()).thenReturn(schema);
        when(schema.getField("productId")).thenReturn(schemaField);
        when(fieldType.getValueSource(schemaField, fp)).thenReturn(productIdValueSource);
        when(request.getSearcher()).thenReturn(searcher);
        when(searcher.getCache("boostCache")).thenReturn(boostCache);

        // product id function values
        when(productIdValueSource.getValues(any(Map.class), any(AtomicReaderContext.class))).thenReturn(productIdFunctionValues);
        for (int i = 0; i <= 10; i++) {
            when(productIdFunctionValues.strVal(i)).thenReturn("prod" + i);
        }
    }

    @Test
    public void testCachedBoosts() throws Exception {
        when(boostCache.get(boostId)).thenReturn(createBoosts());
        ValueSource vs = vsp.parse(fp);

        verifyZeroInteractions(httpClient);

        FunctionValues values = vs.getValues(null, null);
        Assert.assertEquals(0.7f, values.floatVal(0), 0.0f);
        Assert.assertEquals(0.6f, values.floatVal(1), 0.0f);
        Assert.assertEquals(0.5f, values.floatVal(2), 0.0f);
        for (int i = 3; i <= 10; i++) {
            Assert.assertEquals(0.0f, values.floatVal(i), 0.0f);
        }
    }

    @Test
    public void testUncachedBoosts() throws Exception {
        mockHttpResponse(HttpStatus.SC_OK);

        ValueSource vs = vsp.parse(fp);
        FunctionValues values = vs.getValues(null, null);
        Assert.assertEquals(0.7f, values.floatVal(0), 0.0f);
        Assert.assertEquals(0.6f, values.floatVal(1), 0.0f);
        Assert.assertEquals(0.5f, values.floatVal(2), 0.0f);
        for (int i = 3; i <= 10; i++) {
            Assert.assertEquals(0.0f, values.floatVal(i), 0.0f);
        }
    }

    @Test
    public void testNoBoosts() throws Exception {
        mockHttpResponse(HttpStatus.SC_NOT_FOUND);
        ValueSource vs = vsp.parse(fp);
        FunctionValues values = vs.getValues(null, null);
        for (int i = 0; i <= 10; i++) {
            Assert.assertEquals(0.0f, values.floatVal(i), 0.0f);
        }
    }

    @Test
    public void testApiError() throws Exception {
        mockHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        ValueSource vs = vsp.parse(fp);
        FunctionValues values = vs.getValues(null, null);
        for (int i = 0; i <= 10; i++) {
            Assert.assertEquals(0.0f, values.floatVal(i), 0.0f);
        }
    }

    private Map<String, Float> createBoosts() {
        Map<String, Float> boosts = new HashMap<String, Float>();

        boosts.put("prod0", 0.7f);
        boosts.put("prod1", 0.6f);
        boosts.put("prod2", 0.5f);

        return boosts;
    }

    private void mockHttpResponse(int statusCode) throws Exception {
        when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(statusCode);

        if (statusCode == HttpStatus.SC_OK) {
            when(httpResponse.getEntity()).thenReturn(httpEntity);
            String json = "{boosts: [{id:'prod0', value:0.7}, {id:'prod1', value:0.6}, {id:'prod2', value:0.5}]}";
            InputStream stream = new ByteArrayInputStream(json.getBytes());
            when(httpEntity.getContent()).thenReturn(stream);
        }
    }
}
