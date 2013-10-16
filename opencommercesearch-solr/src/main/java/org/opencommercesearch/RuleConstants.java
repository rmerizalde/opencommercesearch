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

/**
 * Constants used  by the RuleManager component.
 */
public class RuleConstants {

    private RuleConstants() {}

    /**
     * Document identifier field
     */
    public static final String FIELD_ID = "id";

    /**
     * Catalog ID field name
     */
    public static final String FIELD_CATALOG_ID = "catalogId";

    /**
     * Site ID field name
     */
    public static final String FIELD_SITE_ID = "siteId";

    /**
     * Target field name
     */
    public static final String FIELD_TARGET = "target";

    /**
     * subTarget field name. Checks if the rule apply to outlet category pages or retail category pages 
     */
    public static final String FIELD_SUB_TARGET = "subTarget";

    /**
     * Wildcard string.
     */
    public static final String WILDCARD = "__all__";

    /**
     * Name of the categories field. This field should be muti-values and allows to filter results based on specific
     * product categories.
     */
    public static final String FIELD_CATEGORY = "category";

    /**
     * Name of  brand Ids field. This field should be muti-values and allows to filter results based on specific brand pages
     */
    public static final String FIELD_BRAND_ID = "brandId";

    /**
     * Field that holds the boost function on boost rules
     */
    public static final String FIELD_BOOST_FUNCTION = "boostFunction";

    /**
     * Multivalued field that stores the facet fields for facet rules
     */
    public static final String FIELD_FACET_ID = "facetId";

    /**
     * Multivalued field that stores the facet ids for facet rules
     */
    public static final String FIELD_FACET_FIELD = "facetField";

    /**
     * Sort priority for a given rule
     */
    public static final String FIELD_SORT_PRIORITY = "sortPriority";

    /**
     * Field that tells whether or not this rule should replace all existing rules of the same type, or be appended to the result
     */
    public static final String FIELD_COMBINE_MODE = "combineMode";

    /**
     * Query field indexed for rules. This field is used to match specific keywords (i.e. a rule should apply only queries that contain certain keywords).
     */
    public static final String FIELD_QUERY = "query";

    /**
     * Boost score for boost rules.
     */
    public static final String FIELD_SCORE = "score";

    /**
     * Date in which this rule will start applying.
     */
    public static final String FIELD_START_DATE = "startDate";

    /**
     * Date in which this rule will end applying.
     */
    public static final String FIELD_END_DATE = "endDate";

    /**
     * Field that specified the type of a rule.
     */
    public static final String FIELD_RULE_TYPE = "ruleType";

    /**
     * Product field that tells whether or not is temporarily out of stock.
     */
    public static final String FIELD_IS_TOOS = "isToos";

    /**
     * Boost field name for ranking rules.
     */
    public static final String FIELD_BOOST = "boost";

    /**
     * List of boosted products on ranking rules.
     */
    public static final String FIELD_BOOSTED_PRODUCTS = "boostedProducts";

    /**
     * List of blocked products on block rules.
     */
    public static final String FIELD_BLOCKED_PRODUCTS = "blockedProducts";

    /**
     * List of blocked products on block rules.
     */
    public static final String FIELD_REDIRECT_URL = "redirectUrl";

    /**
     * On the product catalog, this is the field that stores ancestor category ids. Used by the rules component
     * to find out what's the current category.
     */
    public static final String FIELD_ANCESTOR_CATEGORY = "ancestorCategoryId";

    /**
     * Combine mode value that when specified replaces all existing rule values of the same type.
     */
    public static final String COMBINE_MODE_REPLACE = "Replace";

    /**
     * Combine mode value that when specified appends existing rule values of the same type.
     */
    public static final String COMBINE_MODE_APPEND = "Append";

    /**
     * Outlet value for the sub target field. This indicates that a rule should match only for outlet only searches.
     */
    public static final String SUB_TARGET_OUTLET = "Outlet";

    /**
     * Retail value for the sub target field. This indicates that a rule should match only for retail only searches.
     */
    public static final String SUB_TARGET_RETAIL = "Retail";
}
