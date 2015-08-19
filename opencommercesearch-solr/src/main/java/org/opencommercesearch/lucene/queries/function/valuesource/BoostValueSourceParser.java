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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The boost value source parser is in charge of loading the boosts for the current request. To minimize the amount of
 * calls to the Boost API it keeps a cache with most recently used boosts.
 *
 * todo: support boosts for faceted pages?
 *
 * @author rmerizalde
 */
public class BoostValueSourceParser extends ValueSourceParser {
    public static final String BOOST_API_HOST = "boostApiHost";
    public static final String BOOST_LIMIT = "boostLimit";
    public static final String BOOST_ID = "boostId";
    public static final String TREATMENT_ID = "treatmentId";

    private static Logger log = LoggerFactory.getLogger(BoostValueSourceParser.class);

    private String boostLimit = "10";
    private String boostApiHost = "http://localhost:9000";
    private int soTimeout = 2000;
    private int connectionTimeout = 1000;
    private int maxConnections = 500;
    private int maxConnectionsPerHost = 500;
    protected HttpClient defaultClient;

    @Override
    public void init(NamedList args) {
        boostLimit = getParameter(args, BOOST_LIMIT, boostLimit);
        boostApiHost = getParameter(args, BOOST_API_HOST, boostApiHost);
        soTimeout = getParameter(args, HttpClientUtil.PROP_SO_TIMEOUT, soTimeout);
        connectionTimeout = getParameter(args, HttpClientUtil.PROP_CONNECTION_TIMEOUT, connectionTimeout);
        maxConnections = getParameter(args, HttpClientUtil.PROP_MAX_CONNECTIONS_PER_HOST, maxConnections);
        maxConnectionsPerHost = getParameter(args, HttpClientUtil.PROP_MAX_CONNECTIONS_PER_HOST, maxConnectionsPerHost);

        ModifiableSolrParams clientParams = new ModifiableSolrParams();

        clientParams.set(HttpClientUtil.PROP_MAX_CONNECTIONS, maxConnections);
        clientParams.set(HttpClientUtil.PROP_MAX_CONNECTIONS_PER_HOST, maxConnectionsPerHost);
        clientParams.set(HttpClientUtil.PROP_MAX_CONNECTIONS, maxConnections);
        clientParams.set(HttpClientUtil.PROP_SO_TIMEOUT, soTimeout);
        clientParams.set(HttpClientUtil.PROP_CONNECTION_TIMEOUT, connectionTimeout);
        clientParams.set(HttpClientUtil.PROP_USE_RETRY, false);
        defaultClient = HttpClientUtil.createClient(clientParams);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getParameter(NamedList initArgs, String configKey, T defaultValue) {
        T toReturn = defaultValue;

        if (initArgs != null) {
          T temp = (T) initArgs.get(configKey);
          toReturn = (temp != null) ? temp : defaultValue;
        }

        log.info("Setting {} to: {}", configKey, toReturn);

        return toReturn;
    }

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        String field = fp.parseArg();

        SolrParams params = fp.getReq().getParams();
        String boostId = params.get(BOOST_ID);
        String treatmentId = params.get(TREATMENT_ID);
        if (StringUtils.isNotBlank(treatmentId)) {
            if (log.isDebugEnabled()) {
                log.debug("There is treatment enabled:" + treatmentId);
            }
            boostId += "_" + treatmentId;
        }

        SchemaField f = fp.getReq().getSchema().getField(field);
        ValueSource fieldValueSource = f.getType().getValueSource(f, fp);
        Map<String, Float> boosts = Collections.emptyMap();

        if (StringUtils.isBlank(boostId)) {
            if(log.isDebugEnabled()) {
                log.debug("Missing required 'boostId', skipping boosts");
            }
            return new BoostValueSource(field, fieldValueSource, boosts);
        }

        int queryRows =  NumberUtils.toInt(params.get(CommonParams.ROWS), 0);

        if(queryRows == 0) {
            if(log.isDebugEnabled()) {
                log.debug("Zero rows specified for this query, boosts not needed");
            }
            return new BoostValueSource(field, fieldValueSource, boosts);
        }

        @SuppressWarnings("unchecked")
        SolrCache<String, Map<String, Float>> cache = (SolrCache<String, Map<String, Float>>) fp.getReq().getSearcher().getCache("boostCache");
        return new BoostValueSource(field, fieldValueSource, loadBoosts(boostId, cache));
    }

    /**
     * Loads the boosts for the given boostId through the API.
     *
     * @param boostId is the boost id
     * @return the boost mappings
     */
    private Map<String, Float> loadBoosts(String boostId, SolrCache<String, Map<String, Float>> cache) {
        Map<String, Float> boosts = cache.get(boostId);

        if (boosts != null) {
            if (log.isDebugEnabled()) log.debug("Found " + boosts.size() + " for " + boostId + " in cache");
            return boosts;
        }

        boosts = Collections.emptyMap();

        try {
            String uri = boostApiHost + "/v1/boosts/" + boostId + "?limit=" + boostLimit;
            HttpGet get = new HttpGet(uri);
            HttpResponse response = defaultClient.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                JSONObject json = new JSONObject(EntityUtils.toString(entity));

                boosts = new HashMap<String, Float>();

                if (json.has("boosts")) {
                    JSONArray boostArray = json.getJSONArray("boosts");

                    if (boostArray != null) {
                        for (int i = 0; i < boostArray.length(); i++) {
                            JSONObject boost = boostArray.getJSONObject(i);

                            if (boost.has("id") && boost.has("value")) {
                                boosts.put(boost.getString("id"), ((Double) boost.get("value")).floatValue());
                            }
                        }
                    }
                }

                log.info("Found " + boosts.size() + " for " + boostId);
                cache.put(boostId, boosts);
                return boosts;
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                log.info("No boosts found for " + boostId);
                cache.put(boostId, boosts);
            } else {
                log.error("Cannot retrieve boosts for " + boostId + ". API response code was " + statusCode);
            }
        } catch (ClientProtocolException ex) {
            log.error("Cannot retrieve boosts for " + boostId, ex);
        } catch (IOException ex) {
            log.error("Cannot retrieve boosts for " + boostId, ex);
        }
        return boosts;
    }


}
