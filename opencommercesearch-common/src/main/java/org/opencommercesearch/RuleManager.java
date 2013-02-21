package org.opencommercesearch;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
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
 * 
 */
public class RuleManager<T extends SolrServer> {
    public static final String FIELD_BOOST_FUNCTION = "boostFunction";
    public  static final String FIELD_CATEGORY = "category";

    private static final String WILDCARD = "__all__";

    private Repository searchRepository;
    private RulesBuilder rulesBuilder;
    private SolrServer server;
    private FacetManager facetManager = new FacetManager();
    private Map<String, List<RepositoryItem>> rules;
    private Map<String, SolrDocument> ruleDocs;
    private Map<String, String> strengthMap;

    enum RuleType {
        facetRule() {
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    Set<RepositoryItem> facets = (Set<RepositoryItem>) rule.getPropertyValue(FacetRuleProperty.FACETS);
                    if (facets != null) {
                        for (RepositoryItem facet : facets) {
                            manager.getFacetManager().addFacet(query, facet);
                        }
                    }
                }
            }
        },
        boostRule() {
            void setParams(RuleManager manager, SolrQuery query, List<RepositoryItem> rules, Map<String, SolrDocument> ruleDocs) {
                for (RepositoryItem rule : rules) {
                    @SuppressWarnings("unchecked")
                    List<RepositoryItem> products = (List<RepositoryItem>) rule
                            .getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS);
                    if (products != null && products.size() > 0) {
                        StringBuffer b = new StringBuffer("fixedBoost(productId,");
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


    /**
     * Loads the rules that matches the given query
     * 
     * @param q
     *            is the user query
     * @throws RepositoryException
     *             if an exception happens retrieving a rule from the repository
     * @throws SolrServerException
     *             if an exception happens querying the search engine
     */
    void loadRules(String q, String categoryFilterQuery) throws RepositoryException,
            SolrServerException {
        if (StringUtils.isBlank(q)) {
            throw new IllegalArgumentException("Missing query ");
        }
        SolrQuery query = new SolrQuery("(" + q + ")^2 OR query:__all__");
        int start = 0;
        int rows = 20;
        query.setStart(start);
        query.setRows(rows);
        query.setParam("fl", "id");
        query.add("fl", FIELD_BOOST_FUNCTION);

        StringBuffer filterQueries = new StringBuffer().append("(category:").append(WILDCARD);
        if (StringUtils.isNotBlank(categoryFilterQuery)) {
            filterQueries.append(" OR ").append("category:" + categoryFilterQuery);

        }
        filterQueries.append(") AND ").append("siteId:").append(WILDCARD).append(" AND ").append("catalogId:")
                .append(WILDCARD);
        query.addFilterQuery(filterQueries.toString());
        QueryResponse res = server.query(query);
        
        if (res.getResults() == null || res.getResults().getNumFound() == 0) {
            rules = Collections.emptyMap();
            return;
        }

        rules = new HashMap<String, List<RepositoryItem>>(RuleType.values().length);
        ruleDocs = new HashMap<String, SolrDocument>();
        SolrDocumentList docs = res.getResults();
        int total = (int) docs.getNumFound();
        int processed = 0;
        while (processed < total) {
            processed += (int) docs.getStart();
            for (int i=(int) docs.getStart(); i<docs.size(); i++) {
                ++processed;
                SolrDocument doc = docs.get(i);
                RepositoryItem rule = searchRepository.getItem((String) doc.getFieldValue("id"),
                        SearchRepositoryItemDescriptor.RULE);
                if (rule != null) {
                    String ruleType = (String) rule.getPropertyValue(RuleProperty.RULE_TYPE);
                    List<RepositoryItem> ruleList = (List<RepositoryItem>) rules.get(ruleType);
                    if (ruleList == null) {
                        ruleList = new ArrayList<RepositoryItem>();
                        rules.put(ruleType, ruleList);
                    }
                    ruleList.add(rule);
                    ruleDocs.put(rule.getRepositoryId(), doc);
                } else {
                    //TODO gsegura: add logging that we couldn't find the rule item in the DB
                }
            }
            if (processed < total) {
                query.setStart(processed);
                res = server.query(query);
                docs = res.getResults();
            }
        }
    }

    void setRuleParams(FilterQuery[] filterQueries, RepositoryItem catalog, SolrQuery query)
            throws RepositoryException,
            SolrServerException {
        if (getRules() == null) {
            String categoryFilterQuery = extractCategoryFilterQuery(filterQueries);            
            String queryStr = query.getQuery();
            if(StringUtils.isEmpty(queryStr)){
            	queryStr = query.get("q.alt");
            }
            loadRules(queryStr, categoryFilterQuery);
        }
        setRuleParams(query, getRules());
        setFilterQueries(filterQueries, catalog.getRepositoryId(), query);
    }

    void setRuleParams(SolrQuery query, Map<String, List<RepositoryItem>> rules) {
        if (rules == null) {
            return;
        }
        //TODO gsegura: i think this sort fields are been added twice after adding sorting functionality 
        //need to fix this
        query.addSortField("isToos", ORDER.asc);
        for (Entry<String, List<RepositoryItem>> entry : rules.entrySet()) {
            RuleType type = RuleType.valueOf(entry.getKey());

            if (type != null) {
                type.setParams(this, query, entry.getValue(), ruleDocs);
            }
        }
        query.addSortField("score", ORDER.desc);
    }

    private void setFilterQueries(FilterQuery[] filterQueries, String catalogId, SolrQuery query) {
        query.setFacetPrefix("category", "1." + catalogId);
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

                    category = ++level + FilterQuery.unescapeQueryChars(category.substring(index));
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

        StringBuffer b = new StringBuffer();
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
     * Create a search document representing a rule
     * 
     * @param rule
     *            the repository item to be indexed
     * @return the search document to be indexed
     * @throws RepositoryException
     *             is an exception occurs while retrieving data from the
     *             repository
     */
    SolrInputDocument createRuleDocument(RepositoryItem rule) throws RepositoryException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", rule.getRepositoryId());
        String query = (String) rule.getPropertyValue(RuleProperty.QUERY);
        if (query == null || query.equals("*")) {
            query = WILDCARD;
        }
        doc.setField("query", query);

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> sites = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.SITES);

        if (sites != null && sites.size() > 0) {
            for (RepositoryItem site : sites) {
                doc.addField("siteId", site.getRepositoryId());
            }
        } else {
            doc.setField("siteId", WILDCARD);
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> catalogs = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATALOGS);

        if (catalogs != null && catalogs.size() > 0) {
            for (RepositoryItem catalog : catalogs) {
                doc.addField("catalogId", catalog.getRepositoryId());
            }
        } else {
            doc.setField("catalogId", WILDCARD);
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> categories = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATEGORIES);

        if (categories != null && categories.size() > 0) {
            for (RepositoryItem category : categories) {
                setCategorySearchTokens(doc, category);
            }
        } else {
            doc.setField(FIELD_CATEGORY, WILDCARD);
        }

        if (RuleProperty.TYPE_RANKING_RULE.equals(rule.getPropertyValue(RuleProperty.RULE_TYPE))) {
            String rankAction = null;

            if (BOOST_BY_FACTOR.equals(rule.getPropertyValue(BOOST_BY))) {
                String strength = (String) rule.getPropertyValue(STRENGTH);
                rankAction = mapStrength(strength);
            } else {
                rankAction = (String) rule.getPropertyValue(ATTRIBUTE);
                if (rankAction == null) {
                    rankAction = "1.0";
                }
            }

            // TODO: add support for locales
            String conditionQuery = rulesBuilder.buildRankingRuleFilter(rule, Locale.US);
            StringBuilder boostFunctionQuery = new StringBuilder();

            if (conditionQuery.length() > 0) {
                boostFunctionQuery
                    .append("if(exists(query({!lucene v='")
                    .append(StringUtils.remove(conditionQuery, '\''))
                    .append("'})),")
                    .append(rankAction)
                    .append(",1.0)");
            } else {
                boostFunctionQuery.append(rankAction);
            }

            doc.setField(FIELD_BOOST_FUNCTION, boostFunctionQuery.toString());
        }

        return doc;
    }

    /**
     * Maps the given strength to a boost factor
     *
     * @param strength is the strength name. See RankingRuleProperty
     * @return
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
     * @TODO move this mappings to configuration file
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

    /**
     * Helper method to set the category search token for document
     * 
     * @param doc
     *            the document to be indexed
     * @param category
     *            the category's repository item
     * @throws RepositoryException
     *             if an exception occurs while retrieving category info
     */
    private void setCategorySearchTokens(SolrInputDocument doc, RepositoryItem category) throws RepositoryException {
        if (!"category".equals(category.getItemDescriptor().getItemDescriptorName())) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> searchTokens = (Set<String>) category.getPropertyValue(CategoryProperty.SEARCH_TOKENS);
        if (searchTokens != null) {
            for (String searchToken : searchTokens) {
                doc.addField(FIELD_CATEGORY, searchToken);
            }
        }

        @SuppressWarnings("unchecked")
        List<RepositoryItem> childCategories = (List<RepositoryItem>) category
                .getPropertyValue(CategoryProperty.CHILD_CATEGORIES);

        if (childCategories != null) {
            for (RepositoryItem childCategory : childCategories) {
                setCategorySearchTokens(doc, childCategory);
            }
        }
    }
}

