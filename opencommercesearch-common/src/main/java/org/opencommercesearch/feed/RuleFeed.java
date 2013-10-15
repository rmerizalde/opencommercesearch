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
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.RuleConstants;
import org.opencommercesearch.RulesBuilder;
import org.opencommercesearch.Utils;
import org.opencommercesearch.api.ProductService;
import org.opencommercesearch.repository.*;

import java.sql.Timestamp;
import java.util.*;

import static org.opencommercesearch.repository.RankingRuleProperty.*;
import static org.opencommercesearch.repository.RankingRuleProperty.ATTRIBUTE;

/**
 * This class represents a feed from ATG to the OpenCommerceSearch REST API.
 * <p/>
 * The feed will fetch any matching rules from the database and send them over HTTP to the rules API endpoint, which should index
 * them to Solr.
 *
 * @author Javier Mendez
 */
public class RuleFeed extends BaseRestFeed {

    public static String[] REQUIRED_FIELDS = { "id", "category" };

    private Map<String, String> strengthMap;
    private RulesBuilder rulesBuilder;
    private final String BOTH = "Both";
    /**
     * Return the Endpoint for this feed
     * @return an Endpoint enum representing the endpoint for this feed
     */
    public ProductService.Endpoint getEndpoint() {
        return ProductService.Endpoint.RULES;
    }

    @Override
    protected JSONObject repositoryItemToJson(RepositoryItem rule) throws JSONException, RepositoryException {
        //Convert rule to JSON
        JSONObject ruleJsonObj = new JSONObject();
        ruleJsonObj.put(RuleConstants.FIELD_ID, rule.getRepositoryId());

        String query = (String) rule.getPropertyValue(RuleProperty.QUERY);
        if (query == null || query.equals("*")) {
            query = RuleConstants.WILDCARD;
        }
        else {
            query = query.toLowerCase();
        }

        ruleJsonObj.put(RuleConstants.FIELD_QUERY, query);
        ruleJsonObj.put(RuleConstants.FIELD_SORT_PRIORITY, rule.getPropertyValue(RuleProperty.SORT_PRIORITY));
        ruleJsonObj.put(RuleConstants.FIELD_COMBINE_MODE, rule.getPropertyValue(RuleProperty.COMBINE_MODE));

        //Add the start and end dates
        Timestamp startDate = (Timestamp) rule.getPropertyValue(RuleProperty.START_DATE);
        if(startDate != null) {
            ruleJsonObj.put(RuleConstants.FIELD_START_DATE, Utils.getISO8601Date(startDate.getTime()));
        }

        Timestamp endDate = (Timestamp) rule.getPropertyValue(RuleProperty.END_DATE);
        if(endDate != null) {
            ruleJsonObj.put(RuleConstants.FIELD_END_DATE, Utils.getISO8601Date(endDate.getTime()));
        }

        String target = (String) rule.getPropertyValue(RuleProperty.TARGET);
        if(target != null) {
            target = StringUtils.replace(target, " ", "");
            ruleJsonObj.put(RuleConstants.FIELD_TARGET, new JSONArray().put(target.toLowerCase()));
        }

        String retailOutlet = (String) rule.getPropertyValue(RuleProperty.SUB_TARGET);
        if(retailOutlet != null && !retailOutlet.equals(BOTH)) {
        	ruleJsonObj.put(RuleConstants.FIELD_SUB_TARGET, new JSONArray().put(retailOutlet));
        } else {
        	ruleJsonObj.put(RuleConstants.FIELD_SUB_TARGET, new JSONArray().put(RuleConstants.WILDCARD));        	
        }
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> sites = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.SITES);
        if (sites != null && sites.size() > 0) {
            JSONArray siteIds = new JSONArray();
            for (RepositoryItem site : sites) {
                siteIds.add(site.getRepositoryId());
                siteIds.add(site.getPropertyValue("code"));
            }

            ruleJsonObj.put(RuleConstants.FIELD_SITE_ID, siteIds);
        } else {
            ruleJsonObj.put(RuleConstants.FIELD_SITE_ID, new JSONArray().put(RuleConstants.WILDCARD));
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> catalogs = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATALOGS);
        if (catalogs != null && catalogs.size() > 0) {
            JSONArray catalogIds = new JSONArray();
            for (RepositoryItem catalog : catalogs) {
                catalogIds.add(catalog.getRepositoryId());
            }

            ruleJsonObj.put(RuleConstants.FIELD_CATALOG_ID, catalogIds);
        } else {
            ruleJsonObj.put(RuleConstants.FIELD_CATALOG_ID, new JSONArray().put(RuleConstants.WILDCARD));
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> categories = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.CATEGORIES);

        if (categories != null && categories.size() > 0) {
            Boolean includeSubcategories = (Boolean) rule.getPropertyValue(RuleProperty.INCLUDE_SUBCATEGORIES);

            if (includeSubcategories == null) {
                includeSubcategories = Boolean.FALSE;
            }

            try {
                for (RepositoryItem category : categories) {
                    if (CategoryProperty.ITEM_DESCRIPTOR.equals(category.getItemDescriptor().getItemDescriptorName())) {
                        setCategorySearchTokens(ruleJsonObj, category, includeSubcategories);
                        setCategoryCategoryPaths(ruleJsonObj, category, includeSubcategories);
                    }
                    else {
                        setCategoryCategoryPaths(ruleJsonObj, category, false);
                    }
                }
            }
            catch(RepositoryException e) {
                //Can't load categories
                if(isLoggingError()) {
                    logError("Can't load categories for rule " + rule.getRepositoryId(), e);
                }
            }

            if (ruleJsonObj.get(RuleConstants.FIELD_CATEGORY) == null || ((JSONArray) ruleJsonObj.get(RuleConstants.FIELD_CATEGORY)).isEmpty()) {
                //Don't index this rule, it has no category information.
                if(isLoggingWarning()) {
                    logWarning("Rule " + rule.getRepositoryId() + " has no valid category data associated to it.");
                    return null;
                }
            }
        }
        else {
            ruleJsonObj.put(RuleConstants.FIELD_CATEGORY, new JSONArray().put(RuleConstants.WILDCARD));
        }

        @SuppressWarnings("unchecked")
        Set<RepositoryItem> brands = (Set<RepositoryItem>) rule.getPropertyValue(RuleProperty.BRANDS);
        if (brands != null && brands.size() > 0) {
            JSONArray brandIds = new JSONArray();
        	for (RepositoryItem brand:brands) {
        	    brandIds.add(brand.getRepositoryId());        		
        	}
        	ruleJsonObj.put(RuleConstants.FIELD_BRAND_ID, brandIds);
        } else {
        	ruleJsonObj.put(RuleConstants.FIELD_BRAND_ID, new JSONArray().put(RuleConstants.WILDCARD));
        }           
        
        //Set additional fields required by different rule types.
        String ruleType = (String) rule.getPropertyValue(RuleProperty.RULE_TYPE);
        if (RuleProperty.TYPE_RANKING_RULE.equals(ruleType)) {
            setRankingRuleFields(rule, ruleJsonObj);
        }
        else if(RuleProperty.TYPE_FACET_RULE.equals(ruleType)) {
            setFacetRuleFields(rule, ruleJsonObj);
        }
        else if(RuleProperty.TYPE_REDIRECT_RULE.equals(ruleType)) {
            ruleJsonObj.put(RuleConstants.FIELD_REDIRECT_URL, rule.getPropertyValue(RedirectRuleProperty.URL));
        }
        else if(RuleProperty.TYPE_BLOCK_RULE.equals(ruleType)) {
            Set<RepositoryItem> products = (Set<RepositoryItem>) rule.getPropertyValue(BlockRuleProperty.BLOCKED_PRODUCTS);
            if (products != null && products.size() > 0) {
                JSONArray blockedProducts = new JSONArray();
                for (RepositoryItem product : products) {
                    blockedProducts.add(product.getRepositoryId());
                }

                ruleJsonObj.put(RuleConstants.FIELD_BLOCKED_PRODUCTS, blockedProducts);
            }
        }
        else if(RuleProperty.TYPE_BOOST_RULE.equals(ruleType)) {
            List<RepositoryItem> products = (List<RepositoryItem>) rule.getPropertyValue(BoostRuleProperty.BOOSTED_PRODUCTS);
            if (products != null && products.size() > 0) {
                JSONArray boostedProducts = new JSONArray();
                for (RepositoryItem product : products) {
                    boostedProducts.add(product.getRepositoryId());
                }

                ruleJsonObj.put(RuleConstants.FIELD_BOOSTED_PRODUCTS, boostedProducts);
            }
        }

        ruleJsonObj.put(RuleConstants.FIELD_RULE_TYPE, ruleType);

        return ruleJsonObj;
    }

    @Override
    protected String[] getRequiredItemFields() {
        return REQUIRED_FIELDS;
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
     * Initializes the strength map.
     */
    private void initializeStrengthMap() {
        //TODO move this mappings to configuration file
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
     * Helper method to set the facet fields for a rule JSON object.
     * @param rule Rule to read ranking fields from.
     * @param ruleJsonObj JSON object representing the given rule.
     * @throws JSONException If there are issues formatting category data to JSON.
     */
    private static void setFacetRuleFields(RepositoryItem rule, JSONObject ruleJsonObj) throws JSONException {
        List<RepositoryItem> facets = (List<RepositoryItem>) rule.getPropertyValue(FacetRuleProperty.FACETS);
        JSONArray facetFields = new JSONArray();
        for (RepositoryItem facet : facets) {
            facetFields.add(facet.getPropertyValue(FacetProperty.FIELD));
        }

        ruleJsonObj.put(RuleConstants.FIELD_FACET_FIELD, facetFields);

        JSONArray facetIds = new JSONArray();
        for (RepositoryItem facet : facets) {
            facetIds.add(facet.getRepositoryId());
        }

        ruleJsonObj.put(RuleConstants.FIELD_FACET_ID, facetIds);
    }

    /**
     * Helper method to set the ranking fields for a rule JSON object.
     * @param rule Rule to read ranking fields from.
     * @param ruleJsonObj JSON object representing the given rule.
     * @throws JSONException If there are issues formatting category data to JSON.
     */
    private void setRankingRuleFields(RepositoryItem rule, JSONObject ruleJsonObj) throws JSONException {
        String rankAction;

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
                    .append(StringUtils.replace(conditionQuery, "'", "\\'"))
                    .append("'})),")
                    .append(rankAction)
                    .append(",1.0)");
        } else {
            boostFunctionQuery.append(rankAction);
        }

        ruleJsonObj.put(RuleConstants.FIELD_BOOST_FUNCTION, boostFunctionQuery.toString());
    }

    /**
     * Helper method to set the category path fields for a rule JSON object.
     * @param ruleJsonObj JSON object representing the given rule.
     * @param category the category's repository item.
     * @param includeSubcategories Whether or not subcategories should be included.
     * @throws JSONException If there are issues formatting category data to JSON.
     */
    private void setCategoryCategoryPaths(JSONObject ruleJsonObj, RepositoryItem category, boolean includeSubcategories) throws JSONException {
        Set<String> categoryPaths = Utils.buildCategoryPrefix(category);
        addAll(getCategoriesIfPresent(ruleJsonObj), categoryPaths);

        if (includeSubcategories) {
            @SuppressWarnings("unchecked")
            List<RepositoryItem> childCategories = (List<RepositoryItem>) category.getPropertyValue(CategoryProperty.CHILD_CATEGORIES);
            if (childCategories != null) {
                for (RepositoryItem childCategory : childCategories) {
                    setCategoryCategoryPaths(ruleJsonObj, childCategory, includeSubcategories);
                }
            }
        }
    }

    /**
     * Helper method to set the category search token for a rule JSON object.
     *
     * @param ruleJsonObj JSON object representing the given rule.
     * @param category the category's repository item.
     * @throws RepositoryException if an exception occurs while retrieving category info.
     * @throws JSONException If there are issues formatting category data to JSON.
     */
    private void setCategorySearchTokens(JSONObject ruleJsonObj, RepositoryItem category, boolean includeSubcategories) throws RepositoryException, JSONException {
        @SuppressWarnings("unchecked")
        Set<String> searchTokens = (Set<String>) category.getPropertyValue(CategoryProperty.SEARCH_TOKENS);
        if (searchTokens != null) {
            addAll(getCategoriesIfPresent(ruleJsonObj), searchTokens);
        }

        if (includeSubcategories) {
            @SuppressWarnings("unchecked")
            List<RepositoryItem> childCategories = (List<RepositoryItem>) category.getPropertyValue(CategoryProperty.CHILD_CATEGORIES);

            if (childCategories != null) {
                for (RepositoryItem childCategory : childCategories) {
                    setCategorySearchTokens(ruleJsonObj, childCategory, includeSubcategories);
                }
            }
        }
    }

    /**
     * Helper method to add all items of a collection to the end of a given JSON array.
     * <p/>
     * At the time this method was created, there was a bug in {@link atg.json.JSONArray#addAll(java.util.Collection)} that caused
     * items to be added as the collection itself (i.e. not joining both collections, but adding a single collection item to the array).
     * <p/>
     * Code looked like in JSONArray:
     * <pre>
     * public boolean addAll(Collection c)
     * {
     *    return this.myArrayList.add(c);
     * }
     * </pre>
     *
     * When it should be:
     * <pre>
     * public boolean addAll(Collection c)
     * {
     *    return this.myArrayList.add<b>All</b>(c);
     * }
     * </pre>
     * <p/>
     * If this ever gets corrected, get rid of this method.
     */
    private void addAll(JSONArray array, Collection c) {
        array.addAll(array.length(), c);
    }

    /**
     * Helper method to get the array of categories on the rule object so far, or a new list if not present.
     * @param ruleJsonObj The rule JSON object.
     * @return Json array of existing categories or a new one if no categories existed.
     * @throws JSONException If there are JSON parsing errors.
     */
    private JSONArray getCategoriesIfPresent(JSONObject ruleJsonObj) throws JSONException {
        JSONArray categories;

        try {
            categories = (JSONArray) ruleJsonObj.get(RuleConstants.FIELD_CATEGORY);
        }
        catch(JSONException e) {
            categories = new JSONArray();
        }

        ruleJsonObj.put(RuleConstants.FIELD_CATEGORY, categories);
        return categories;
    }

    public RulesBuilder getRulesBuilder() {
        return rulesBuilder;
    }

    public void setRulesBuilder(RulesBuilder rulesBuilder) {
        this.rulesBuilder = rulesBuilder;
    }
}
