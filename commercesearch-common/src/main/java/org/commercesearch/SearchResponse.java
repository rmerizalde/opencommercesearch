package org.commercesearch;

import org.apache.solr.client.solrj.response.QueryResponse;

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

    SearchResponse(QueryResponse queryResponse, RuleManager ruleManager) {
        this.queryResponse = queryResponse;
        this.ruleManager = ruleManager;
    }

    public QueryResponse getQueryResponse() {
        return queryResponse;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

}
