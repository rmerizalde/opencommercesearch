package org.apache.solr.handler.component;

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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.opencommercesearch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * Component that will read rules from the configured core and filter or modify search results accordingly.
 * <p/>
 * A simple example is a boosting rule, that provides parameters to move up/down products in the results.
 * @author Javier Mendez
 */
public class RuleManagerComponent extends SearchComponent implements SolrCoreAware {

    /**
     * Page size when looking for rules or facets.
     */
    public static final int PAGE_SIZE = 40;

    /**
     * Ranking separator used to split a ranking rule into a boost rule and a customRankingRule
     */
    public static final String RANKING_SEPARATOR = "|";
    
    /**
     * Name of the parameter used when we split a ranking rule using the custom separator 
     */
    public static final String CUSTOM_RANKING_PARAM_NAME = "customRankingRule";
    
    /**
     * Logger instance
     */
    private static Logger logger = LoggerFactory.getLogger(RuleManagerComponent.class);

    /**
     * The core container, used to get rules and facet cores.
     */
    CoreContainer coreContainer = null;

    /**
     * Base name of the rules core
     */
    String rulesCoreBaseName = "rule";

    /**
     * Base name of the facets core
     */
    String facetsCoreBaseName = "facets";

    /**
     * Base name of the categories core.
     */
    String categoriesCoreBaseName = "categories";

    /**
     * The core to query for rule data.
     */
    String rulesCoreName;

    /**
     * The core to query for facet fields data.
     */
    String facetsCoreName;

    /**
     * Binder to transform Lucene docs to objects.
     */
    DocumentObjectBinder binder = new DocumentObjectBinder();

    /**
     * Enumeration of valid page types understood by RuleManager
     *
     * <ul>
     *     'search' : all regular search pages
     * </ul>
     * <ul>
     *     'category' : all category based pages (including brand categories)
     * </ul>
     * <ul>
     *     'rule' : all rule based pages
     * <ul/>
     */
    public enum PageType { search, category, rule}

    @Override
    public void init(NamedList args)
    {
        SolrParams initArgs = SolrParams.toSolrParams(args);

        //Load params if defined
        String rulesCoreBaseName = initArgs.get("rulesCore");
        if(rulesCoreBaseName != null) {
            this.rulesCoreBaseName = rulesCoreBaseName;
        }

        String facetsCoreBaseName = initArgs.get("facetsCore");
        if(facetsCoreBaseName != null) {
            this.facetsCoreBaseName = facetsCoreBaseName;
        }
    }

    /**
     * Whenever indexes change, update core references so the latest copy is used.
     */
    public void inform(SolrCore core) {
        //Update core container with latest reference.
        coreContainer = core.getCoreDescriptor().getCoreContainer();

        rulesCoreName = getCoreName(core, rulesCoreBaseName);
        facetsCoreName = getCoreName(core, facetsCoreBaseName);

        if(rulesCoreName == null || facetsCoreName == null) {
            logger.error("Running on core " + core.getName() + " that is not 'Preview' or 'Public'. Cannot associate appropiate Rules and Facets cores due that.");
        }
        else {
            logger.debug("Rules core name: " + rulesCoreName);
            logger.debug("Facets core name: " + facetsCoreName);
        }
    }

    @Override
    public String getDescription() {
        return "Rule Manager - adds additional params to a search request based on business rules";
    }

    @Override
    public String getSource() {
        return "https://raw.github.com/rmerizalde/opencommercesearch/master/opencommercesearch-solr/src/main/java/org/apache/solr/handler/component/RulesManagerComponent.java";
    }

    @Override
    public void prepare(ResponseBuilder rb) {
        SolrParams requestParams = rb.req.getParams();
        Boolean rulesEnabled = requestParams.getBool(RuleManagerParams.RULE);

        if(rulesEnabled == null || !rulesEnabled) {
            logger.debug("Rule param is set to false. Nothing to do here.");
            return;
        }

        if(rulesCoreName == null || facetsCoreName == null) {
            logger.warn("There are initialization errors, bypassing this request.");
            return;
        }

        PageType pageType = getPageType(requestParams);

        if(pageType == null) {
            //Do nothing, this request is not supported by the RuleManager component
            logger.debug("No page type param was defined, bypassing this request.");
            return;
        }

        try {
            //Get matching rules from rulesCore
            Map<RuleType, List<Document>> rulesMap = searchRules(requestParams, pageType);

            //Now add params to the original query
            MergedSolrParams augmentedParams;

            //Check if there are any redirect rules
            if(rulesMap.containsKey(RuleType.redirectRule)) {
                //we need a bunch of the request param default values to avoid exceptions, but to
                //cut the rest of calculations and return only the redirect we are putting the q to be empty
                augmentedParams = new MergedSolrParams(requestParams);
                augmentedParams.set(CommonParams.Q, StringUtils.EMPTY);
                List<Document> redirects = rulesMap.get(RuleType.redirectRule);
                if(redirects != null && redirects.size() > 0) {
                    rb.rsp.add("redirect_url", redirects.get(0).get(RuleConstants.FIELD_REDIRECT_URL));
                }
                else {
                    //Shouldn't happen
                    logger.error("Found no redirect rules although there should be, bypassing this request");
                    return;
                }
            }
            else {
                augmentedParams = new MergedSolrParams(requestParams);

                //Set sorting options (re-arrange sort incoming fields)
                String[] sortFields = requestParams.getParams(CommonParams.SORT);

                //Always push the products out of stock to the bottom, even when manual boosts have been selected
                augmentedParams.setSort(RuleConstants.FIELD_IS_TOOS, SolrQuery.ORDER.asc);

                //Now put any incoming sort options (if any)
                if (sortFields != null) {
                    Set<String> sortFieldSet = new HashSet<String>(sortFields.length);

                    for (String sortField : sortFields) {
                        String[] parts = StringUtils.split(sortField, ' ');
                        String fieldName = parts[0];
                        String order = parts[1];

                        if (!("score".equals(fieldName) || sortFieldSet.contains(fieldName))) {
                            augmentedParams.addSort(fieldName, SolrQuery.ORDER.valueOf(order));
                            sortFieldSet.add(fieldName); //Ensure there are no duplicates
                        }
                    }
                }

                //Initialize facet manager
                FacetHandler facetHandler = new FacetHandler();
                
                for (Map.Entry<RuleType, List<Document>> rule: rulesMap.entrySet()) {
                    RuleType type = rule.getKey();

                    if (type != null) {
                        //If there are boost rules, these will change the sort parameters.
                        type.setParams(this, augmentedParams, rule.getValue(), facetHandler);
                    }
                }

                //Finally add the score and version fields to the sorting spec. These will be a tie breaker when other sort specs are added
                augmentedParams.addSort("score", SolrQuery.ORDER.desc);
                augmentedParams.addSort("_version_", SolrQuery.ORDER.desc);

                Map<String, NamedList> facets = facetHandler.getFacets();
                setFilterQueries(facets, requestParams, augmentedParams);
                rb.rsp.add("rule_facets", facets.values());
            }

            logger.debug("Augmented request: " + augmentedParams.toString());

            String debug = requestParams.get(RuleManagerParams.DEBUG);
            if(debug != null && debug.equals("true")) {
                rb.rsp.add("rule_debug", getDebugInfo(rulesMap, augmentedParams));
            }

            rb.req.setParams(augmentedParams);
        }
        catch(Throwable e) {
            logger.error("Failed to handle this request", e);
        }
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        //Nothing to do here
    }

    /**
     * Gets the corresponding core name based on the current core instance type (public or preview).
     * <p/>
     * For example, if performing a search to the public french product catalog, and the core baseName is <b>'rule'</b>, this method will return
     * <b>'rulePublic_fr'</b>.
     * @param core Current core serving a request.
     * @param baseName Base name of the core to build the name for.
     * @return Valid core name based on the current core instance type or null if the current core does not have a valid format (expecting '%baseName%instanceType' - example 'catalogPublic').
     */
    private String getCoreName(SolrCore core, String baseName) {
        return getCoreName(core, baseName, true);
    }

    /**
     * Gets the corresponding core name based on the current core instance type (public or preview).
     * <p/>
     * For example, if performing a search to the public french product catalog, and the core baseName is <b>'rule'</b>, this method will return
     * <b>'rulePublic_fr'</b>.
     * <p/>
     * You can specify whether or not the locale should be included. In the above example '_fr' could be removed if needed.
     * @param core Current core serving a request.
     * @param baseName Base name of the core to build the name for.
     * @param hasLocale Whether or not the given core has locale languages. If not, the language suffix won't be added to the obtained core name.
     * @return Valid core name based on the current core instance type or null if the current core does not have a valid format (expecting '%baseName%instanceType' - example 'catalogPublic').
     */
    private String getCoreName(SolrCore core, String baseName, boolean hasLocale) {
        String coreName = core.getName();
        String lowerCaseCoreName = coreName.toLowerCase();

        int suffixStart;

        if((suffixStart = lowerCaseCoreName.indexOf("public")) < 0 && (suffixStart = lowerCaseCoreName.indexOf("preview")) < 0) {
            return null;
        }

        if(hasLocale) {
            return baseName + coreName.substring(suffixStart);
        }
        else {
            return baseName + coreName.substring(suffixStart, coreName.length() - 3); //Remove locale, i.e. '_fr', or '_en'
        }
    }

    /**
     * Get a search handler from the configured core. This method assumes the search handler is looked for is '/select'.
     * @param core Core to get the search handler from.
     * @return The search handler at the '/select' path.
     * @throws IOException If the provided core is null or the search handler does not exist.
     */
    public SearchHandler getSearchHandler(SolrCore core) throws IOException {
        if(core == null) {
            throw new IOException("Cannot process any requests because a required core was not found. Check that you created a core called " + rulesCoreName + ", and " + facetsCoreName + ".");
        }

        SearchHandler searchHandler = (SearchHandler) core.getRequestHandler("/select");

        if(searchHandler == null) {
            throw new IOException("Cannot process any requests because the core search handler was not found. Check that " + core.getName() + " has a valid '/select' request handler configured.");
        }

        return searchHandler;
    }

    /**
     * Get the type of page performing this request (search, category, rule page).
     * @param requestParams Current request params.
     * @return The page type based on the provided request params, or null if none was specified.
     */
    private static PageType getPageType(SolrParams requestParams) {
        String pageTypeParam = requestParams.get(RuleManagerParams.PAGE_TYPE);
        return pageTypeParam == null? null : PageType.valueOf(pageTypeParam);
    }

    /**
     * Search for matching rules. Any found rules are added to a map to process them later.
     * @param requestParams Incoming search params.
     * @param pageType Current page type.
     * @return Map of rules where the key is the rule type.
     * @throws IOException If there are issues getting the rules core.
     */
    private Map<RuleType, List<Document>> searchRules(SolrParams requestParams, PageType pageType) throws IOException {
        Map<RuleType, List<Document>> rulesMap = new HashMap<RuleType, List<Document>>();

        //Prepare query to rules index
        String qParam = requestParams.get(CommonParams.Q);
        SolrQuery ruleParams = getRulesQuery(requestParams, pageType);

        if(ruleParams == null) {
            //Do nothing, this request is not supported by the RuleManager component
            return rulesMap;
        }

        SolrCore rulesCore = coreContainer.getCore(rulesCoreName);
        SearchHandler searchHandler = getSearchHandler(rulesCore);
        RefCounted<SolrIndexSearcher> rulesSearcher = rulesCore.getSearcher();
        int ruleCounter = 0, matchedRules = 0;

        try {
            do {
                ruleParams.set(CommonParams.START, ruleCounter);

                logger.debug("Searching rules core - rules processed so far " + ruleCounter);
                SolrQueryResponse response = new SolrQueryResponse();
                rulesCore.execute(searchHandler, new LocalSolrQueryRequest(rulesCore, ruleParams), response);
                ResultContext result = (ResultContext) response.getValues().get("response");

                if(result != null) {
                    DocList rules = result.docs;
                    DocIterator ruleIterator = rules.iterator();
                    matchedRules = rules.matches();

                    //Process rules
                    while(ruleIterator.hasNext()) {
                        ruleCounter++;
                        Document ruleDoc = rulesSearcher.get().doc(ruleIterator.nextDoc());

                        if (PageType.search == pageType && !filterExactMatch(qParam, ruleDoc)) {
                            // Skip this rule as an exact match was required, but got only a partial match.
                            continue;
                        }

                        RuleType ruleType = RuleType.valueOf(ruleDoc.get(RuleConstants.FIELD_RULE_TYPE));
                        addRuleToMap(rulesMap, ruleType, ruleDoc);
                    }
                }
                else {
                    logger.error("An error occurred when searching for matching rules. Processed rules so far: " + ruleCounter, response.getException());
                    break;
                }
            }
            while(ruleCounter < matchedRules);

            logger.debug("Rules found: " + matchedRules);
        }
        finally {
            rulesSearcher.decref();
        }

        return rulesMap;
    }

    /**
     * Checks if the given rule was configured as an exact match and the query 'q' matches the query in the rule.
     * <p/>
     * This method is used to filter out rules when exact matches are required. Rules query field is tokenized, so it
     * will always matches even when the query is not exactly the same.
     * <p/>
     * We want to support either partial or exact query matches. So if the rule query field value is between brackets, it
     * tells the rule manager that an exact match is desired. If the rule query has brackets and is not an exact match, this
     * method will return false.
     * @param q Query to check for
     * @param rule Rule whose query field should match the given query
     * @return true if the given query ('q') matches the rule query field and an exact was required, true otherwise.
     */
    private boolean filterExactMatch(String q, Document rule) {
        String targetQuery = rule.get(RuleConstants.FIELD_QUERY);

        if (isExactMatch(targetQuery)) {
            targetQuery = removeBrackets(targetQuery).toLowerCase();
            return targetQuery.equals(q.toLowerCase());
        }
        return true;
    }

    /**
     * Return true if the given query is an exact match, owtherwise false. The exact match syntax is to put the query
     * in the rule between brackets. For example ("the bike").
     */
    private boolean isExactMatch(String query) {
        return query != null && query.startsWith("[") && query.endsWith("]");

    }

    /**
     * Just a helper method to strip off the characters
     */
    private String removeBrackets(String query) {
        return query.substring(1, query.length() - 1);
    }

    /**
     * Prepares a query to the rules index based on the type of request received and the provided params.
     * <p/>
     * This query is intended to match any rules that apply for the current request.
     * @param requestParams Parameters on the current request being processed (i.e. search, category browsing, rule based pages, etc).
     * @param pageType Type of page to which this request belongs to.
     * @return Query ready to be sent to the rules core, to fetch all rules that apply for the given request.
     * @throws IOException If there are missing required values.
     */
    protected SolrQuery getRulesQuery(SolrParams requestParams, PageType pageType) throws IOException {
        String catalogId = requestParams.get(RuleManagerParams.CATALOG_ID);

        if(catalogId == null) {
            logger.debug("No catalog ID provided, bypassing this request.");
            return null;
        }

        SolrQuery ruleParams = new SolrQuery("*:*");

        //Common params
        ruleParams.set(CommonParams.ROWS, PAGE_SIZE);
        ruleParams.set(CommonParams.FL, RuleConstants.FIELD_ID, RuleConstants.FIELD_BOOST_FUNCTION, RuleConstants.FIELD_FACET_FIELD, RuleConstants.FIELD_COMBINE_MODE, RuleConstants.FIELD_QUERY, RuleConstants.FIELD_CATEGORY);

        //Sorting options
        ruleParams.addSort(RuleConstants.FIELD_SORT_PRIORITY, SolrQuery.ORDER.asc);
        ruleParams.addSort(RuleConstants.FIELD_SCORE, SolrQuery.ORDER.asc);
        ruleParams.addSort(RuleConstants.FIELD_ID, SolrQuery.ORDER.asc);

        //Filter queries
        StringBuilder reusableStringBuilder = new StringBuilder();
        ruleParams.addFilterQuery(getTargetFilter(reusableStringBuilder, pageType, requestParams.get(CommonParams.Q)));
        ruleParams.addFilterQuery(getCategoryFilter(reusableStringBuilder, requestParams.get(RuleManagerParams.CATEGORY_FILTER)));
        ruleParams.addFilterQuery(getSiteFilter(reusableStringBuilder, requestParams.getParams(RuleManagerParams.SITE_IDS)));
        ruleParams.addFilterQuery(getBrandFilter(reusableStringBuilder, requestParams.get(RuleManagerParams.BRAND_ID)));
        ruleParams.addFilterQuery(getSubTargetFilter(reusableStringBuilder, isOutletRequest(requestParams.getParams(CommonParams.FQ))));

        StringBuilder catalogFilter = reuseStringBuilder(reusableStringBuilder);
        catalogFilter.append(RuleConstants.FIELD_CATALOG_ID).append(":").append(RuleConstants.WILDCARD).append(" OR ").append(RuleConstants.FIELD_CATALOG_ID).append(":").append(catalogId);
        ruleParams.addFilterQuery(catalogFilter.toString());

        //Notice how the current datetime (NOW wildcard on Solr) is rounded to days (NOW/DAY). This allows filter caches
        //to be reused and hopefully improve performance. If you don't round to day, NOW is very precise (up to milliseconds); so every query
        //would need a new entry on the filter cache...
        //Also, notice that NOW/DAY is midnight from last night, and NOW/DAY+1DAY is midnight today.
        //The below query is intended to match rules with null start or end dates, or start and end dates in the proper range.
        StringBuilder dateFilter = reuseStringBuilder(reusableStringBuilder);
        dateFilter.append("-(((").append(RuleConstants.FIELD_START_DATE).append(":[* TO *]) AND -(").append(RuleConstants.FIELD_START_DATE).append(":[* TO NOW/DAY+1DAY])) OR (").append(RuleConstants.FIELD_END_DATE).append(":[* TO *] AND -").append(RuleConstants.FIELD_END_DATE).append(":[NOW/DAY+1DAY TO *]))");
        ruleParams.addFilterQuery(dateFilter.toString());
        return ruleParams;
    }

    /**
     * Gets the target filter
     * @param reusableStringBuilder String builder to put data into
     * @param pageType The current page type
     * @param q the query param from original request
     * @return Target filter for rules
     */
    private String getTargetFilter(StringBuilder reusableStringBuilder, PageType pageType, String q) throws IOException {
        StringBuilder targetFilter = reuseStringBuilder(reusableStringBuilder);

        if(pageType == PageType.search) {
            if(StringUtils.isEmpty(q)) {
                throw new IOException("Cannot process search request because the 'q' param is empty.");
            }

            targetFilter.append("(target:allpages OR target:searchpages) AND ((");
            targetFilter.append(ClientUtils.escapeQueryChars(q));
            targetFilter.append(")^2 OR query:__all__)");
        }
        else {
            targetFilter.append("target:allpages OR target:categorypages");
        }

        return targetFilter.toString();
    }

    /**
     * Gets the category filter
     * @param reusableStringBuilder String builder to put data into
     * @param categoryToken Category search tokens to filter out rules
     * @return Category filter for rules
     */
    private String getCategoryFilter(StringBuilder reusableStringBuilder, String categoryToken) {
        StringBuilder categoryFilter = reuseStringBuilder(reusableStringBuilder);

        categoryFilter.append(RuleConstants.FIELD_CATEGORY).append(":").append(RuleConstants.WILDCARD);

        if (StringUtils.isNotBlank(categoryToken)) {
            categoryFilter.append(" OR ").append(RuleConstants.FIELD_CATEGORY).append(":").append(categoryToken);
        }

        return categoryFilter.toString();
    }

    /**
     * Gets the site filter
     * @param reusableStringBuilder String builder to put data into
     * @param sites Array of site codes to look for
     * @return The site filter
     */
    private String getSiteFilter(StringBuilder reusableStringBuilder, String[] sites) {
        StringBuilder siteFilter = reuseStringBuilder(reusableStringBuilder);

        siteFilter.append(RuleConstants.FIELD_SITE_ID).append(":").append(RuleConstants.WILDCARD);

        if (sites != null) {
            for(String site : sites) {
                siteFilter.append(" OR ").append(RuleConstants.FIELD_SITE_ID).append(":").append(site);
            }
        }

        return siteFilter.toString();
    }

    /**
     * Gets the brand filter
     * @param reusableStringBuilder String builder to put data into
     * @param brandId Brand ID used to filter by if any
     * @return The brand filter
     */
    private String getBrandFilter(StringBuilder reusableStringBuilder, String brandId) {
        StringBuilder brandFilter = reuseStringBuilder(reusableStringBuilder);

        brandFilter.append(RuleConstants.FIELD_BRAND_ID).append(":").append(RuleConstants.WILDCARD);

        if(StringUtils.isNotBlank(brandId)) {
            brandFilter.append(" OR ").append(RuleConstants.FIELD_BRAND_ID).append(":").append(brandId);
        }

        return brandFilter.toString();
    }

    /**
     * Gets the subTarget filter
     * @param reusableStringBuilder String builder to put data into
     * @param isOutletPage whether or not the current page is outlet
     * @return The subTarget filter
     */
    private String getSubTargetFilter(StringBuilder reusableStringBuilder, boolean isOutletPage) {
        StringBuilder subTargetFilter = reuseStringBuilder(reusableStringBuilder);

        subTargetFilter.append(RuleConstants.FIELD_SUB_TARGET).append(":").append(RuleConstants.WILDCARD);
        subTargetFilter.append(" OR ").append(RuleConstants.FIELD_SUB_TARGET).append(":");

        if(isOutletPage) {
            subTargetFilter.append(RuleConstants.SUB_TARGET_OUTLET);
        }
        else {
            subTargetFilter.append(RuleConstants.SUB_TARGET_RETAIL);
        }

        return subTargetFilter.toString();
    }

    /**
     * Resets a string builder so it can be reused.
     * @param stringBuilder String builder to reuse.
     * @return new empty string builder based on the one provided.
     */
    private StringBuilder reuseStringBuilder(StringBuilder stringBuilder) {
        stringBuilder.reverse().setLength(0);
        return stringBuilder;
    }

    /**
     * Tells whether or not the current search should include outlet results or not.
     * <p/>
     * This is done by inspecting the filter queries set for the current search request.
     * @param filterQueries Array of filter queries to inspect.
     * @return True if the current search will include outlet results, false otherwise.
     */
    private boolean isOutletRequest(String[] filterQueries) {
        if(filterQueries != null) {
            for(String filterQuery : filterQueries) {
                if(filterQuery.startsWith(SearchConstants.FIELD_IS_CLOSEOUT + ":true")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get core for facet fields.
     * @return SolrCore for facet fields, or null if not found.
     */
    SolrCore getFacetsCore() {
        return coreContainer.getCore(facetsCoreName);
    }

    /**
     * Helper method that updates a given rule map.
     * @param rulesMap Rule map to update.
     * @param ruleType The type of rule being added.
     * @param ruleDoc Rule doc to insert into the map.
     */
    private void addRuleToMap(Map<RuleType, List<Document>> rulesMap, RuleType ruleType, Document ruleDoc) {
        List<Document> ruleList = rulesMap.get(ruleType);

        if(ruleList == null) {
            ruleList = new LinkedList<Document>();
            rulesMap.put(ruleType, ruleList);
        }

        ruleList.add(ruleDoc);
    }

    /**
     * Set filter queries params, used for faceting and filtering of results.
     * @param requestParams Original request params
     * @param ruleParams The new query parameters added by the rules component.
     */
    private void setFilterQueries(Map<String, NamedList> facets, SolrParams requestParams, MergedSolrParams ruleParams) {
        
        String isRulePage = requestParams.get("rulePage");
        String catalogId = requestParams.get(RuleManagerParams.CATALOG_ID);
        ruleParams.setFacetPrefix(RuleConstants.FIELD_CATEGORY, "1." + catalogId + ".");
        ruleParams.addFilterQuery(RuleConstants.FIELD_CATEGORY + ":0." + catalogId);

        String categoryFilter = requestParams.get(RuleManagerParams.CATEGORY_FILTER);
        //if we don't have a category filter, or we are in a rule page, skip adding the facet prefix
        //to the category facet. This is cause we don't index the rule path in the category facet
        if(StringUtils.isNotBlank(categoryFilter) && !BooleanUtils.toBoolean(isRulePage)) {
            int index = categoryFilter.indexOf(SearchConstants.CATEGORY_SEPARATOR);
            if (index != -1) {
                int level = Integer.parseInt(categoryFilter.substring(0, index));

                categoryFilter = ++level + FilterQuery.unescapeQueryChars(categoryFilter.substring(index)) + ".";
                ruleParams.setFacetPrefix(RuleConstants.FIELD_CATEGORY, categoryFilter);
            }
        }
        
        String[] filterQueries = requestParams.getParams("rule.fq");
        if (filterQueries == null) {
            return;
        }
        
        Map<String, Set<String>> multiExpressionFilters = new HashMap<String, Set<String>>();
        for(String filterQuery: filterQueries) {
            
            String[] parts = StringUtils.split(filterQuery, ":", 2);
            if (parts.length != 2) {
                logger.error("Invalid filter query: " + filterQuery);
                continue;
            } else {
                String fieldName = parts[0];
                String fieldExpression = parts[1];
                
                if (fieldName.equals("category")) {
                    int index = fieldExpression.indexOf(SearchConstants.CATEGORY_SEPARATOR);
                    if (index != -1) {
                        int level = Integer.parseInt(fieldExpression.substring(0, index));

                        fieldExpression = ++level + FilterQuery.unescapeQueryChars(fieldExpression.substring(index)) + ".";
                        ruleParams.setFacetPrefix("category", fieldExpression);
                    }
                }
                NamedList facetItem = facets.get(fieldName);
                if(facetItem != null) {
                    String multiSelect = (String) facetItem.get("isMultiSelect");
                    if (facetItem != null && StringUtils.isNotBlank(multiSelect)) {
                        if (FacetHandler.getBooleanFromField(multiSelect)) {
                            Set<String> expressions = multiExpressionFilters.get(fieldName);
                            if (expressions == null) {
                                expressions = new HashSet<String>();
                                multiExpressionFilters.put(fieldName, expressions);
                            }
                            expressions.add(fieldExpression);
                            continue;
                        }
                    }
                }
                
            }
            
            ruleParams.addFilterQuery(filterQuery);
        }
        
        StringBuilder b = new StringBuilder();
        for (Entry<String, Set<String>> entry : multiExpressionFilters.entrySet()) {
            String operator = " OR ";
            String fieldName = entry.getKey();
            b.append("{!tag=").append(fieldName).append("}");
            for (String expression : entry.getValue()) {
                b.append(fieldName).append(FilterQuery.SEPARATOR).append(expression).append(operator);
            }
            b.setLength(b.length() - operator.length());
            ruleParams.addFilterQuery(b.toString());
            b.setLength(0);
        }

    }

    /**
     * Creates a named list with all rules that are being applied to the current request.
     * <p/>
     * The final named list can be added to the search response, for debugging purposes.
     * @param rulesMap Map of rules that were found by the manager component.
     * @param ruleParams List of params that will be added to the original query due found rules.
     * @return named list can be added to the search response, for debugging purposes.
     */
    private NamedList<Object> getDebugInfo(Map<RuleType, List<Document>> rulesMap, SolrParams ruleParams) {
        NamedList<Object> debugInfo = new NamedList<Object>();
        debugInfo.add("rules", rulesMap);
        debugInfo.add("ruleParams", ruleParams.toString());

        return debugInfo;
    }

    /**
     * Enumeration of valid rule types understood by RuleManager
     */
    public enum RuleType {
        facetRule() {
            void setParams(RuleManagerComponent component, MergedSolrParams ruleParams, List<Document> rules, FacetHandler facetHandler) throws IOException {
                for(Document rule : rules) {
                    if(RuleConstants.COMBINE_MODE_REPLACE.equals(rule.get(RuleConstants.FIELD_COMBINE_MODE))) {
                        facetHandler.clear();
                    }
                    
                    String[] facetField = rule.getValues(RuleConstants.FIELD_FACET_FIELD);
                    if(facetField != null) {
	                    String facetsQueryString = getQueryString(rule);
	                    if(StringUtils.isBlank(facetsQueryString)) {
	                        continue;
	                    }
	                    
	                    facetHandler.addFacet(facetField);
	                    searchFacets(component, facetsQueryString, facetHandler);
                    }
                }

                facetHandler.setParams(ruleParams);
            }

            /**
             * Search for matching facets.
             * @param component The rules component.
             * @param facetsQueryString Facets query string to search for.
             * @param facetHandler Facet handler where found facets will be stored.
             * @throws IOException If can't lookup for facet fields.
             */
            private void searchFacets(RuleManagerComponent component, String facetsQueryString, FacetHandler facetHandler) throws IOException {
                SolrCore facetsCore = component.getFacetsCore();
                SearchHandler searchHandler = component.getSearchHandler(facetsCore);
                RefCounted<SolrIndexSearcher> facetsSearcher = facetsCore.getSearcher();

                try {
                    //Prepare query to rules index
                    SolrQuery params = new SolrQuery(facetsQueryString);
                    params.set(CommonParams.ROWS, PAGE_SIZE);
                    int facetCounter = 0, matchedFacets = 0;

                    do {
                        params.set(CommonParams.START, facetCounter);

                        logger.debug("Searching facets core - facets processed so far " + facetCounter);
                        SolrQueryResponse response = new SolrQueryResponse();
                        facetsCore.execute(searchHandler, new LocalSolrQueryRequest(facetsCore, params), response);

                        ResultContext result = (ResultContext) response.getValues().get("response");

                        if(result != null) {
                            DocList facets = result.docs;
                            DocIterator facetsIterator = facets.iterator();
                            matchedFacets = facets.matches();

                            while(facetsIterator.hasNext()) {
                                facetCounter++;
                                int facetId = facetsIterator.nextDoc();
                                facetHandler.addFacet(facetsSearcher.get().doc(facetId));
                            }
                        }
                        else {
                            logger.error("An error occurred when searching for facets. Processed facets so far: " + facetCounter, response.getException());
                            break;
                        }
                    }
                    while(facetCounter < matchedFacets);

                    logger.debug("Matched facets: " + matchedFacets);
                }
                finally {
                    facetsSearcher.decref();
                }
            }

            /**
             * Gets a valid facets query out of a given rule facet ids.
             * @param rule Rule to get the ids from.
             * @return A query string composed of the given rule facet ids. Null if there were no facet IDs.
             */
            private String getQueryString(Document rule) {
                String[] facetIds = rule.getValues(RuleConstants.FIELD_FACET_ID);
                
                if(facetIds.length > 0) {
                    StringBuilder facetsQueryString = new StringBuilder();
  
                    for(String facetId : facetIds) {
                        facetsQueryString.append(RuleConstants.FIELD_ID).append(":").append(facetId).append(" OR ");
                    }

                    facetsQueryString.setLength(facetsQueryString.length() - 4);

                    return facetsQueryString.toString();
                }
                else {
                    return  null;
                }
            }
        },
        boostRule() {
            void setParams(RuleManagerComponent component, MergedSolrParams query, List<Document> rules, FacetHandler facetHandler) {
                String[] sortFields = query.getParams(CommonParams.SORT);
                if (sortFields != null && sortFields.length > 1) {
                    //User has selected a sorting option, ignore manual boosts
                    return;
                }

                if(rules.size() >= 1) {
                    String[] products = rules.get(0).getValues(RuleConstants.FIELD_BOOSTED_PRODUCTS);

                    if (products != null && products.length > 0) {
                        StringBuilder b = new StringBuilder("fixedBoost(productId,");

                        for (String product : products) {
                            b.append("'").append(product).append("',");
                        }

                        b.setLength(b.length() - 1);
                        b.append(")");
                        query.addSort(b.toString(), SolrQuery.ORDER.asc);
                    }

                    //TODO handle multiple boost rules
                }
            }
        },
        blockRule() {
            void setParams(RuleManagerComponent component, MergedSolrParams query, List<Document> rules, FacetHandler facetHandler) {
                for (Document rule : rules) {
                    String[] products = rule.getValues(RuleConstants.FIELD_BLOCKED_PRODUCTS);

                    if (products != null) {
                        for (String product : products) {
                            query.addFilterQuery("-productId:" + product);
                        }
                    }
                }
            }
        },
        redirectRule() {
            @Override
            void setParams(RuleManagerComponent component, MergedSolrParams query, List<Document> rules, FacetHandler facetHandler) {
                //for redirect rules we don't need a enum entry to add parameters to the query
                //but to avoid an exception while on:  RuleType.valueOf(entry.getKey());  we are adding
                //this empty entry. The redirect itself will be handled by the rule manager
            }

        },
        rankingRule() {
            @Override
            void setParams(RuleManagerComponent component, MergedSolrParams query, List<Document> rules, FacetHandler facetHandler) {
                for (Document rule : rules) {
                    String boostFunction = rule.get(RuleConstants.FIELD_BOOST_FUNCTION);
                    
                    if (boostFunction != null) {
                        if(StringUtils.contains(boostFunction, RANKING_SEPARATOR)) {
                            // If the ranking rule has our custom ranking separator, split the rule by that
                            // separator.
                            // Set the first part of the rule as a regular boost param  in the solr query
                            // and set the second part using the CUSTOM_BOOST_PARAM_NAME
                            // This is useful to provide a solr expression to a custom component so that you can take advantage of
                            // the a/b test framework to test many variations of the same expression
                            String[] boostRules = StringUtils.split(boostFunction, RANKING_SEPARATOR);
                            if(boostRules.length == 2) {
                                query.add(RuleConstants.FIELD_BOOST, boostRules[0]);
                                query.add(CUSTOM_RANKING_PARAM_NAME, boostRules[1]);
                            } else {
                                logger.error("Incorrect use of the '"+ RANKING_SEPARATOR + "' operator in the following ranking rule:" + boostFunction);
                            }
                        } else {
                            query.add(RuleConstants.FIELD_BOOST, boostFunction);
                        }
                    }
                }
            }
        };

        abstract void setParams(RuleManagerComponent component, MergedSolrParams query, List<Document> rules, FacetHandler facetHandler) throws IOException;
    }
}
