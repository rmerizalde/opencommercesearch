package org.commercesearch;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.commercesearch.Facet.Filter;
import org.commercesearch.repository.RangeFacetProperty;

import atg.repository.RepositoryItem;

/**
 * Bean class that return the query response from the search engine and the
 * business rules applied to query
 * 
 * @author rmerizalde
 * 
 */
public class SearchResponse {

    private QueryResponse queryResponse;
    private RuleManager ruleManager;
    private FilterQuery[] filterQueries;

    SearchResponse(QueryResponse queryResponse, RuleManager ruleManager, FilterQuery[] filterQueries) {
        this.queryResponse = queryResponse;
        this.ruleManager = ruleManager;
        this.filterQueries = filterQueries;
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

    public List<Facet> getFacets() {
        List<Facet> facets = new ArrayList<Facet>();
        FacetManager manager = getRuleManager().getFacetManager();

        for (FacetField facetField : queryResponse.getFacetFields()) {
            Facet facet = new Facet();

            facet.setName(manager.getFacetName(facetField));
            List<Filter> filters = new ArrayList<Filter>(facetField.getValueCount());
            int pos = 0;
            for (Count count : facetField.getValues()) {
                Filter filter = new Filter();
                filter.setName(manager.getCountName(count));
                filter.setCount(count.getCount());
                filter.setPath(manager.getCountPath(count, getFilterQueries()));
                filters.add(filter);
            }
            facet.setFilter(filters);
            facets.add(facet);
        }
        getRangeFacets(facets);
        return facets;
    }
    
    private void getRangeFacets(List<Facet> facets) {
        FacetManager manager = getRuleManager().getFacetManager();

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
            facets.add(facet);
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
}
