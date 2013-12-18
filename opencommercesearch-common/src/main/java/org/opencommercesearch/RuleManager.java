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

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.opencommercesearch.repository.*;

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;

import static org.opencommercesearch.repository.RankingRuleProperty.*;

/**
 * This class provides functionality to load the rules that matches a given
 * query or triggers.
 * 
 * @author rmerizalde
 */
public class RuleManager<T extends SolrServer> {
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_ID = "id";
    public static final String FIELD_BOOST_FUNCTION = "boostFunction";
    public static final String FIELD_FACET_FIELD = "facetField";
    public static final String FIELD_EXPERIMENTAL = "experimental";
    public static final String FIELD_SORT_PRIORITY = "sortPriority";
    public static final String FIELD_COMBINE_MODE = "combineMode";
    public static final String FIELD_QUERY = "query";
    public static final String FIELD_SCORE = "score";
    public static final String FIELD_START_DATE = "startDate";
    public static final String FIELD_END_DATE = "endDate";
    public static final int DEFAULT_START = 0;
    public static final int DEFAULT_ROWS = 20;

    public static final String WILDCARD = "__all__";
    
    private Repository searchRepository;
    private RulesBuilder rulesBuilder;
    private SolrServer server;
    private FacetManager facetManager = new FacetManager();
    private Map<String, List<RepositoryItem>> rules;
    private Map<String, SolrDocument> ruleDocs;
    private Map<String, String> strengthMap;

    /**
     * The time it took to load rules from Solr
     */
    int loadRulesTime = 0;

    enum RuleType {
        facetRule() {
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                if (rules == null || rules.size() == 0) {
                    return;
                }

                FacetManager facetManager = manager.getFacetManager();

                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    SolrDocument doc = ruleDocs.get(rule.getRepositoryId());

                    if (FacetRuleProperty.COMBINE_MODE_REPLACE.equals(doc.getFieldValue(FacetRuleProperty.COMBINE_MODE))) {
                        facetManager.clear();
                    }

                    List<RepositoryItem> facets = (List<RepositoryItem>) rule.getPropertyValue(FacetRuleProperty.FACETS);
                    if (facets != null) {
                        for (RepositoryItem facet : facets) {
                            facetManager.addFacet(facet);
                        }
                    }
                }
                facetManager.setParams(query);
            }
        },
        boostRule() {
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                String[] sortFields = query.getSortFields();
                if (sortFields != null && sortFields.length > 1) {
                    // user has selected a sorting option, ignore manual boosts
                    return;
                }

                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    List<RepositoryItem> products = (List<RepositoryItem>) rule
                            .getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS);
                    if (products != null && products.size() > 0) {
                        StringBuilder b = new StringBuilder("fixedBoost(productId,");
                        for (RepositoryItem product : products) {
                            b.append("'").append(product.getRepositoryId()).append("',");
                        }
                        b.setLength(b.length() - 1);
                        b.append(")");
                        query.addSortField(b.toString(), ORDER.asc);
                    }

                    // @todo handle multiple boost rules
                    break;
                }

            }
        },
        blockRule() {
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {

                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    Set<RepositoryItem> products = (Set<RepositoryItem>) rule
                            .getPropertyValue(BlockRuleProperty.BLOCKED_PRODUCTS);

                    if (products != null) {
                        for (RepositoryItem product : products) {
                            query.addFilterQuery("-productId:" + product.getRepositoryId());
                        }
                    }
                }

            }
        },
        redirectRule() {

            @Override
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                //TODO gsegura: for redirect rule we don't need a enum entry to add parameters to the query
                //but to avoid an exception while on:  RuleType.valueOf(entry.getKey());  we are adding
                //this empty entry. The redirect itself will be handled by the abstractSearchServer
            }
            
        },
        rankingRule() {
            @Override
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                for (RepositoryItem rule : rules) {
                    SolrDocument doc = ruleDocs.get(rule.getRepositoryId());

                    if (doc != null) {
                        String boostFunction = (String) doc.getFieldValue(FIELD_BOOST_FUNCTION);

                        if (boostFunction != null) {
                            query.add("boost", boostFunction);
                        }
                    }
                }
            }
        };
        
        abstract void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs);
    }

    RuleManager(Repository searchRepository, RulesBuilder rulesBuilder, T server) {
      this.searchRepository = searchRepository;
      this.rulesBuilder = rulesBuilder;
      this.server = server;
    }
    
    public FacetManager getFacetManager() {
        return facetManager;
    }

    public Map<String, List<RepositoryItem>> getRules() {
        return rules;
    }

    private void buildRuleLists (String ruleType, RepositoryItem rule, SolrDocument doc) {
        List<RepositoryItem> ruleList = rules.get(ruleType);
        if (ruleList == null) {
            ruleList = new ArrayList<RepositoryItem>();
            rules.put(ruleType, ruleList);
        }
        ruleList.add(rule);
        ruleDocs.put(rule.getRepositoryId(), doc);
    }

    /**
     * Loads the rules that matches the given query
     * 
     * @param q is the user query
     * @param categoryPath is the current category path, used to filter out rules (i.e. rule based pages)
     * @param categoryFilterQuery is the current category search token that will be used for filtering out rules and facets
     * @param isSearch indicates if we are browsing or searching the site
     * @param isRuleBasedPage tells whether or not we are on a rule based page
     * @param catalog the current catalog we are browsing/searching
     * @param isOutletPage tells whether or not the current page is outlet
     * @param brandId is the current brand id currently browsed, if any.
     * @throws RepositoryException if an exception happens retrieving a rule from the repository
     * @throws SolrServerException if an exception happens querying the search engine
     */
    void loadRules(String q, String categoryPath, String categoryFilterQuery, boolean isSearch, boolean isRuleBasedPage, RepositoryItem catalog, boolean isOutletPage, String brandId, Set<String> includeExperiments, Set<String> excludeExperiments) throws RepositoryException, SolrServerException {
        if (isSearch && StringUtils.isBlank(q)) {
            throw new IllegalArgumentException("Missing query");
        }

        SolrQuery query = new SolrQuery("*:*");
        query.setStart(DEFAULT_START);
        query.setRows(DEFAULT_ROWS);
        query.addSort(FIELD_SORT_PRIORITY, ORDER.asc);
        query.addSort(FIELD_SCORE, ORDER.asc);
        query.addSort(FIELD_ID, ORDER.asc);
        query.add(CommonParams.FL, FIELD_ID, FIELD_BOOST_FUNCTION, FIELD_FACET_FIELD, FIELD_COMBINE_MODE, FIELD_QUERY, FIELD_CATEGORY);

        StringBuilder reusableStringBuilder = new StringBuilder();
        query.addFilterQuery(getTargetFilter(reusableStringBuilder, isSearch, q));
        query.addFilterQuery(getCategoryFilter(reusableStringBuilder, categoryFilterQuery, categoryPath));
        query.addFilterQuery(getSiteFilter(reusableStringBuilder, catalog));
        query.addFilterQuery(getBrandFilter(reusableStringBuilder, brandId));
        query.addFilterQuery(getSubTargetFilter(reusableStringBuilder, isOutletPage));

        StringBuilder catalogFilter = reuseStringBuilder(reusableStringBuilder);
        catalogFilter.append("catalogId:").append(WILDCARD).append(" OR ").append("catalogId:").append(catalog.getRepositoryId());
        query.addFilterQuery(catalogFilter.toString());

        //Notice how the current datetime (NOW wildcard on Solr) is rounded to days (NOW/DAY). This allows filter caches
        //to be reused and hopefully improve performance. If you don't round to day, NOW is very precise (up to milliseconds); so every query
        //would need a new entry on the filter cache...
        //Also, notice that NOW/DAY is midnight from last night, and NOW/DAY+1DAY is midnight today.
        //The below query is intended to match rules with null start or end dates, or start and end dates in the proper range.
        query.addFilterQuery("-(((startDate:[* TO *]) AND -(startDate:[* TO NOW/DAY+1DAY])) OR (endDate:[* TO *] AND -endDate:[NOW/DAY+1DAY TO *]))");

        int queryTime = 0;
        QueryResponse res = server.query(query);
        queryTime += res.getQTime();

        if (res.getResults() == null || res.getResults().getNumFound() == 0) {
            rules = Collections.emptyMap();
            loadRulesTime = queryTime;
            return;
        }

        rules = new HashMap<String, List<RepositoryItem>>(RuleType.values().length);
        ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocumentList docs = res.getResults();
        int total = (int) docs.getNumFound();
        int processed = 0;
        while (processed < total) {
            for (int i = 0; i < docs.size(); i++) {
                ++processed;
                SolrDocument doc = docs.get(i);

                if (isSearch && !matchesQuery(q, doc)) {
                    // skip this rule
                    continue;
                }

                RepositoryItem rule = searchRepository.getItem((String) doc.getFieldValue("id"),
                        SearchRepositoryItemDescriptor.RULE);

                //for rule based categories, include all facet rules and ranking rules of only that category
                if (rule != null) {
                                        
                    if(excludeExperiments.contains(rule.getPropertyValue(RuleProperty.ID))) {
                        continue;
                    }
                    
                    Boolean experimental = (Boolean) doc.getFieldValue(FIELD_EXPERIMENTAL);
                    if(experimental != null && experimental && !includeExperiments.contains(rule.getPropertyValue(RuleProperty.ID))) {
                        continue;
                    }
                    
                    String ruleType = (String) rule.getPropertyValue(RuleProperty.RULE_TYPE);
                    if(ruleType.equals(RuleProperty.TYPE_FACET_RULE)) {
                        buildRuleLists(ruleType, rule, doc);
                    }
                    else {
                        if (categoryPath != null && isRuleBasedPage) {
                            List<String> ruleCategories = (List<String>) doc.getFieldValue(FIELD_CATEGORY);
                            if(ruleCategories != null) {
                                if(ruleCategories.contains(categoryPath)) {
                                    buildRuleLists(ruleType, rule, doc);
                                }
                            }
                        } else {
                            buildRuleLists(ruleType, rule, doc);
                        }
                   }
                } else {
                    //TODO gsegura: add logging that we couldn't find the rule item in the DB
                }
            }
            if (processed < total) {
                query.setStart(processed);
                res = server.query(query);
                queryTime += res.getQTime();
                docs = res.getResults();
            }
        }

        loadRulesTime = queryTime;
    }

    /**
     * Gets the target filter
     * @param reusableStringBuilder String builder to put data into
     * @return Target filter for rules
     */
    private String getTargetFilter(StringBuilder reusableStringBuilder, boolean isSearch, String q) {
        StringBuilder targetFilter = reuseStringBuilder(reusableStringBuilder);

        if (isSearch) {
            targetFilter.append("(target:allpages OR target:searchpages) AND ((");
            targetFilter.append(ClientUtils.escapeQueryChars(q.toLowerCase()));
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
     * @param categoryFilterQuery Category search tokens to filter out rules
     * @param categoryPath Current category path to get rule based pages if any
     * @return Category filter for rules
     */
    private String getCategoryFilter(StringBuilder reusableStringBuilder, String categoryFilterQuery, String categoryPath) {
        StringBuilder categoryFilter = reuseStringBuilder(reusableStringBuilder);
        categoryFilter.append("category:").append(WILDCARD);

        if (StringUtils.isNotBlank(categoryFilterQuery)) {
            categoryFilter.append(" OR ").append("category:").append(categoryFilterQuery);
        }

        if (categoryPath != null) {
            categoryFilter.append(" OR ").append("category:").append(categoryPath);
        }

        return categoryFilter.toString();
    }

    /**
     * Gets the site filter
     * @param reusableStringBuilder String builder to put data into
     * @param catalog Catalog repository item used to get the sites from
     * @return The site filter
     */
    private String getSiteFilter(StringBuilder reusableStringBuilder, RepositoryItem catalog) {
        StringBuilder siteFilter = reuseStringBuilder(reusableStringBuilder);
        siteFilter.append("siteId:").append(WILDCARD);
        Set<String> siteSet = (Set<String>) catalog.getPropertyValue("siteIds");
        if (siteSet != null) {
            for(String site : siteSet) {
                siteFilter.append(" OR ").append("siteId:").append(site);
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
        brandFilter.append("brandId:").append(WILDCARD);
        if(StringUtils.isNotBlank(brandId)) {
            brandFilter.append(" OR ").append("brandId:").append(brandId);
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
        subTargetFilter.append("subTarget:").append(WILDCARD);
        if(isOutletPage) {
            subTargetFilter.append(" OR ").append("subTarget:").append(RuleConstants.SUB_TARGET_OUTLET);
        } else {
            subTargetFilter.append(" OR ").append("subTarget:").append(RuleConstants.SUB_TARGET_RETAIL);
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
     * Returns true if the given rule was configured as an exact match and the query q matches the query in the rule
     */
    private boolean matchesQuery(String q, SolrDocument rule) {
        String targetQuery = (String) rule.getFieldValue(FIELD_QUERY);

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

    void setRuleParams(SolrQuery query, boolean isSearch, boolean isRuleBasedPage, String categoryPath, FilterQuery[] filterQueries, RepositoryItem catalog, boolean isOutletPage, String brandId)
            throws RepositoryException,
            SolrServerException {
        if (getRules() == null) {
            String categoryFilterQuery = extractCategoryFilterQuery(filterQueries);
            String includeExp[] =(String []) query.getParams("includeRules");
            String excludeExp[] =(String []) query.getParams("excludeRules");
            
            Set<String> includeExperiments = new HashSet<String>();
            if(includeExp != null) {
                includeExperiments = new HashSet<String>(Arrays.asList(includeExp));
            }
            Set<String> excludeExperiments = new HashSet<String>();
            if(excludeExp != null) {
                excludeExperiments = new HashSet<String>(Arrays.asList(excludeExp));
            }
            loadRules(query.getQuery(), categoryPath, categoryFilterQuery, isSearch, isRuleBasedPage, catalog, isOutletPage, brandId, includeExperiments, excludeExperiments);
        }
        setRuleParams(query, getRules());
        setFilterQueries(filterQueries, catalog.getRepositoryId(), query);
    }

    // Helper method to process the rules for this request
    void setRuleParams(SolrQuery query, Map<String, List<RepositoryItem>> rules) {
        setRuleParams(query, rules, ruleDocs);
    }

    // Helper method to process the rules for this request
    void setRuleParams(SolrQuery query, Map<String, List<RepositoryItem>> rules, Map<String, SolrDocument> ruleDocs) {
        if (rules == null) {
            return;
        }

        String[] sortFields = query.getSortFields();

        // always push the products out of stock to the bottom, even when manual boosts have been selected
        query.setSortField("isToos", ORDER.asc);

        // add sort specs after the isToos and possibly
        if (sortFields != null) {
            Set sortFieldSet = new HashSet(sortFields.length);

            for (String sortField : sortFields) {
                String[] parts = StringUtils.split(sortField, ' ');
                String fieldName = parts[0];
                String order = parts[1];

                if (!("score".equals(fieldName) || sortFieldSet.contains(fieldName))) {
                    query.addSortField(fieldName, ORDER.valueOf(order));
                    sortFieldSet.add(fieldName);
                }
            }
        }

        for (Entry<String, List<RepositoryItem>> entry : rules.entrySet()) {
            RuleType type = RuleType.valueOf(entry.getKey());

            if (type != null) {
                type.setParams(this, query, entry.getValue(), ruleDocs);
            }
        }

        // finally add the score field to the sorting spec. Score will be a tie breaker when other sort specs are added
        query.addSortField("score", ORDER.desc);
    }

    void setFilterQueries(FilterQuery[] filterQueries, String catalogId, SolrQuery query) {
        query.setFacetPrefix("category", "1." + catalogId + ".");
        query.addFilterQuery("category:" + "0." + catalogId);

        if (filterQueries == null) {
            return;
        }
        
        Map<String, Set<String>> multiExpressionFilters = new HashMap<String, Set<String>>();

        for (FilterQuery filterQuery : filterQueries) {
            if (filterQuery.getFieldName().equals("category")) {
                String category = filterQuery.getExpression();
                int index = category.indexOf(SearchConstants.CATEGORY_SEPARATOR);
                if (index != -1) {
                    int level = Integer.parseInt(category.substring(0, index));

                    category = ++level + FilterQuery.unescapeQueryChars(category.substring(index)) + ".";
                    query.setFacetPrefix("category", category);
                }
            }
            RepositoryItem facetItem = getFacetManager().getFacetItem(filterQuery.getFieldName());
            if (facetItem != null) {
                Boolean isMultiSelect = (Boolean) facetItem.getPropertyValue(FacetProperty.IS_MULTI_SELECT);
                if (isMultiSelect != null && isMultiSelect) {
                    //query.addFilterQuery( +  + + filterQuery);
                    Set<String> expressions = multiExpressionFilters.get(filterQuery.getFieldName());
                    if (expressions == null) {
                        expressions = new HashSet<String>();
                        multiExpressionFilters.put(filterQuery.getFieldName(), expressions);
                    }
                    expressions.add(filterQuery.getExpression());
                    continue;
                }
            }
            query.addFilterQuery(filterQuery.toString());
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
            query.addFilterQuery(b.toString());
            b.setLength(0);
        }
    }

    private String extractCategoryFilterQuery(FilterQuery[] filterQueries) {
        if (filterQueries == null) {
            return null;
        }

        for (FilterQuery filterQuery : filterQueries) {
            if (filterQuery.getFieldName().equals("category")) {
                return filterQuery.getExpression();
            }
        }

        return null;
    }

    /**
     * Maps the given strength to a boost factor
     *
     * @param strength is the strength name. See RankingRuleProperty
     */
    protected String mapStrength(String strength) {
        if (strengthMap == null) {
            initializeStrengthMap();
        }

        String boostFactor = strengthMap.get(strength);

        if (boostFactor == null) {
            boostFactor = "1.0";
        }
        return boostFactor;
    }

    /**
     * Initializes the strenght map.
     *
     * TODO move this mappings to configuration file
     */
    private void initializeStrengthMap() {
        strengthMap = new HashMap<String, String>(STRENGTH_LEVELS);

        strengthMap.put(STRENGTH_MAXIMUM_DEMOTE, Float.toString(1/10f));
        strengthMap.put(STRENGTH_STRONG_DEMOTE, Float.toString(1/5f));
        strengthMap.put(STRENGTH_MEDIUM_DEMOTE, Float.toString(1/2f));
        strengthMap.put(STRENGTH_WEAK_DEMOTE, Float.toString(1/1.5f));
        strengthMap.put(STRENGTH_NEUTRAL, Float.toString(1f));
        strengthMap.put(STRENGTH_WEAK_BOOST, Float.toString(1.5f));
        strengthMap.put(STRENGTH_MEDIUM_BOOST, Float.toString(2f));
        strengthMap.put(STRENGTH_STRONG_BOOST, Float.toString(5f));
        strengthMap.put(STRENGTH_MAXIMUM_BOOST, Float.toString(10f));
    }

    public int getLoadRulesTime() {
        return loadRulesTime;
    }

    public void setLoadRulesTime(int loadRulesTime) {
        this.loadRulesTime = loadRulesTime;
    }
}

