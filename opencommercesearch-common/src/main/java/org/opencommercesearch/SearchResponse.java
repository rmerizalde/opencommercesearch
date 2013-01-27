package org.opencommercesearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.opencommercesearch.Facet.Filter;
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

    private QueryResponse queryResponse;
    private RuleManager ruleManager;
    private FacetManager facetManager;
    private FilterQuery[] filterQueries;
    private String redirectResponse;
    private List<CategoryGraph> categoryGraph;

    SearchResponse(QueryResponse queryResponse, RuleManager ruleManager, FilterQuery[] filterQueries, String redirectResponse) {
        this.queryResponse = queryResponse;
        this.ruleManager = ruleManager;
        this.filterQueries = filterQueries;
        this.redirectResponse = redirectResponse;
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

    public void removeFacet(String name){
        for (FacetField facetField : queryResponse.getFacetFields()) {
            if(name.equals(facetField.getName())){
                queryResponse.getFacetFields().remove(facetField);
                break;
            }
        }
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
        getQueryFacets(facets);

        return facets;
    }
    
    private void getRangeFacets(List<Facet> facets) {
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

    private void getQueryFacets(List<Facet> facets) {
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
                facet.setFilter(filters);
                facets.add(facet);
            }
            Filter filter = new Filter();
            filter.setName(Utils.getRangeName(fieldName, expression));
            filter.setPath(manager.getCountPath(query, fieldName, fieldName + ':' + expression, filterQueries));
            filter.setCount(count);
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

    public String getRedirectResponse() {
        return redirectResponse;
    }

    public List<CategoryGraph> getCategoryGraph() {
        return categoryGraph;
    }

    public void setCategoryGraph(List<CategoryGraph> categoryGraph) {
        this.categoryGraph = categoryGraph;
    }

}
