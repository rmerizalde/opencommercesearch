package org.opencommercesearch;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.common.params.FacetParams;
import org.opencommercesearch.Facet.Filter;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.RangeFacetProperty;

import atg.repository.RepositoryItem;

/**
 * Bean class that return the query response from the search engine and the
 * business rules applied to query
 * 
 * @author rmerizalde
 * 
 */
public class SearchResponse {

    private SolrQuery query;
    private QueryResponse queryResponse;
    private RuleManager ruleManager;
    private FacetManager facetManager;
    private FilterQuery[] filterQueries;
    private String redirectResponse;
    private List<CategoryGraph> categoryGraph;
    private boolean matchesAll;
    private String correctedTerm;
    
    SearchResponse(SolrQuery query, QueryResponse queryResponse, RuleManager ruleManager, FilterQuery[] filterQueries, String redirectResponse, String correctedTerm, boolean matchesAll) {
        this.query = query;
        this.queryResponse = queryResponse;
        this.ruleManager = ruleManager;
        this.filterQueries = filterQueries;
        this.redirectResponse = redirectResponse;
        this.matchesAll = matchesAll;
        this.correctedTerm = correctedTerm;
    }

    public QueryResponse getQueryResponse() {
        return queryResponse;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public FilterQuery[] getFilterQueries() {
        return filterQueries;
    }

    public String getRedirectResponse() {
        return redirectResponse;
    }

    public List<CategoryGraph> getCategoryGraph() {
        return categoryGraph;
    }

    public void setCategoryGraph(List<CategoryGraph> categoryGraph) {
        this.categoryGraph = categoryGraph;
    }

    public boolean matchesAll() {
        return matchesAll;
    }

    public String getCorrectedTerm() {
        return correctedTerm;
    }

    public void removeFacet(String name){
        for (FacetField facetField : queryResponse.getFacetFields()) {
            if(name.equals(facetField.getName())){
                queryResponse.getFacetFields().remove(facetField);
                break;
            }
        }
    }
    
    private Set<String> getFacetBlacklist (String facetName) {
        HashSet<String> blackList = new HashSet<String>();

            RepositoryItem facetItem =  getRuleManager().getFacetManager().getFacetItem(facetName);
            if (facetItem != null) {
                Set<String> facetBlacklist = (Set<String>) facetItem.getPropertyValue(FacetProperty.BLACKLIST);
                if (facetBlacklist != null && facetBlacklist.size() > 0) {
                    blackList.addAll(facetBlacklist);
                }
            }

        return blackList;
    }
    
    public List<Facet> getFacets() {
        Map<String, Facet> facetMap = new HashMap<String, Facet>();
        FacetManager manager = getRuleManager().getFacetManager();

        for (FacetField facetField : queryResponse.getFacetFields()) {
            Facet facet = new Facet();
            
            facet.setName(manager.getFacetName(facetField));
            facet.setMinBuckets(manager.getFacetMinBuckets(facetField));
            facet.setMultiSelect(manager.isMultiSelectFacet(facetField));
            
            String uiType = manager.getFacetUIType(facetField);
            Map<String, String> metadata = new HashMap<String, String>();
            if(StringUtils.isNotBlank(uiType)) {            	
            	metadata.put("uiWidgetType", uiType);
            }
            facet.setMetadata(metadata);
            
            List<Filter> filters = new ArrayList<Filter>(facetField.getValueCount());
            String prefix = query.getFieldParam(facet.getName(), FacetParams.FACET_PREFIX);
            
            Set<String> facetBlackList = getFacetBlacklist(facetField.getName());
            
            for (Count count : facetField.getValues()) {
                String filterName = manager.getCountName(count, prefix);
                if (facetBlackList.contains(filterName)) {
                    continue;
                }
                
                Filter filter = new Filter();
                filter.setName(filterName);
                filter.setCount(count.getCount());
                filter.setPath(manager.getCountPath(count, getFilterQueries()));
                filter.setFilterQuery(count.getAsFilterQuery());
                filter.setSelected(count.getFacetField().getName(), filterName, filterQueries);
                filters.add(filter);
            }
            facet.setFilter(filters);
            facetMap.put(facetField.getName(), facet);
        }
        getRangeFacets(facetMap);
        getQueryFacets(facetMap);

        List<Facet> sortedFacets = new ArrayList<Facet>(facetMap.size());

        for (String fieldName : new String[] {"category", "categoryPath"}) {
            Facet facet = facetMap.get(fieldName);
            if (facet != null) {
                sortedFacets.add(facet);
            }
        }

        for (String fieldName : manager.facetFieldNames()) {
            Facet facet = facetMap.get(fieldName);
            if (facet != null) {
                sortedFacets.add(facet);
            }
        }

        return sortedFacets;
    }
    
    private void getRangeFacets(Map<String, Facet> facetMap) {
        FacetManager manager = getRuleManager().getFacetManager();

        if (getQueryResponse().getFacetRanges() == null) {
            return;
        }

        for (RangeFacet<Integer, Integer> range : getQueryResponse().getFacetRanges()) {
            Facet facet = new Facet();
            facet.setName(manager.getFacetName(range));
            
            List<Filter> filters = new ArrayList<Filter>();

            Filter beforeFilter = createBeforeFilter(range);
            if (beforeFilter != null) {
                filters.add(beforeFilter);
            }


            RangeFacet.Count prevCount = null;
            for (RangeFacet.Count count : range.getCounts()) {
                if (prevCount == null) {
                    prevCount = count;
                    continue;
                }
                filters.add(createRangeFilter(range.getName(), Utils.RESOURCE_IN_RANGE,
                        prevCount.getValue(), count.getValue(), prevCount.getCount()));
                prevCount = count;
            }
            
            if (prevCount != null) {
                RepositoryItem facetItem = manager.getFacetItem(range.getName());
                Boolean hardened = (Boolean) facetItem.getPropertyValue(RangeFacetProperty.HARDENED);
                Integer value2 = (Integer) facetItem.getPropertyValue(RangeFacetProperty.END);
                if (hardened == null || !hardened) {
                    Integer gap = (Integer) facetItem.getPropertyValue(RangeFacetProperty.GAP);
                    value2 = Math.round(Float.parseFloat(prevCount.getValue()));
                    value2 += gap;
                }
                filters.add(createRangeFilter(range.getName(), Utils.RESOURCE_IN_RANGE, prevCount.getValue(),
                        value2.toString(), prevCount.getCount()));
            }
            
            Filter afterFilter = createAfterFilter(range);
            if (afterFilter != null) {
                filters.add(afterFilter);
            }

            facet.setFilter(filters);
            facetMap.put(range.getName(), facet);
        }
    }

    private Filter createBeforeFilter(RangeFacet<Integer, Integer> range) {
        if (range.getBefore() == null || range.getBefore().intValue() == 0) {
            return null;
        }
        FacetManager manager = getRuleManager().getFacetManager();
        RepositoryItem item = manager.getFacetItem(range.getName());
        Integer rangeStart = (Integer) item.getPropertyValue(RangeFacetProperty.START);

        return createRangeFilter(range.getName(), Utils.RESOURCE_BEFORE, "*", rangeStart.toString(), range.getBefore()
                .intValue());
    }

    private Filter createAfterFilter(RangeFacet<Integer, Integer> range) {
        if (range.getAfter() == null || range.getAfter().intValue() == 0) {
            return null;
        }
        FacetManager manager = getRuleManager().getFacetManager();
        RepositoryItem item = manager.getFacetItem(range.getName());
        Integer rangeEnd = (Integer) item.getPropertyValue(RangeFacetProperty.END);

        return createRangeFilter(range.getName(), Utils.RESOURCE_AFTER, rangeEnd.toString(), "*", range.getAfter()
                .intValue());
    }
        
    private Filter createRangeFilter(String fieldName, String key, String value1, String value2, int count) {
        Filter filter = new Filter();
        value1 = removeDecimals(value1);
        value2 = removeDecimals(value2);
        filter.setName(Utils.getRangeName(fieldName, key, value1, value2));
        filter.setCount(count);
        String filterQuery = fieldName + ":[" + value1 + " TO " + value2 + "]";
        FacetManager manager = getRuleManager().getFacetManager();
        filter.setPath(manager.getCountPath(fieldName, fieldName, filterQuery,
                filterQueries));

        return filter;
    }

    private String removeDecimals(String number) {

        int index = number.indexOf(".");
        if (index != -1) {
            return number.substring(0, index);
        }
        return number;
    }

    private void getQueryFacets(Map<String, Facet> facetMap) {
        FacetManager manager = getRuleManager().getFacetManager();

        Map<String, Integer> queryFacets = getQueryResponse().getFacetQuery();

        if (queryFacets == null) {
            return;
        }

        Facet facet = null;
        String facetFieldName = "";
        List<Filter> filters = null;

        for (Entry<String, Integer> entry : queryFacets.entrySet()) {

            Integer count = entry.getValue();
            if (count == 0) {
                continue;
            }

            String query = entry.getKey();
            String[] parts = getFacetQueryParts(query);
            if (parts == null) {
                continue;
            }

            String fieldName = parts[0];
            String expression = parts[1];

            if (!facetFieldName.equals(fieldName)) {
                facetFieldName = fieldName;
                facet = new Facet();

                filters = new ArrayList<Filter>();
                facet.setName(manager.getFacetName(fieldName));
                facet.setMultiSelect(manager.isMultiSelectFacet(fieldName));
                facet.setFilter(filters);
                facetMap.put(fieldName, facet);
            }
            Filter filter = new Filter();
            filter.setName(FilterQuery.unescapeQueryChars(Utils.getRangeName(fieldName, expression)));
            String filterQuery = fieldName + ':' + expression;
            filter.setPath(manager.getCountPath(expression, fieldName, filterQuery, filterQueries));
            filter.setCount(count);
            filter.setSelected(fieldName, expression, filterQueries);
            filters.add(filter);
        }
    }

    private String[] getFacetQueryParts(String query) {
        String[] parts = StringUtils.split(query, ':');
        if (parts.length == 2) {
            int index = parts[0].indexOf('}');
            if (index != -1) {
                parts[0] = parts[0].substring(index + 1);
            }
            return parts;
        }
        return null;
    }
}
