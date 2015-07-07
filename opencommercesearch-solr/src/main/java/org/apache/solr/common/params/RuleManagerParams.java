package org.apache.solr.common.params;

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
 * Group Collapse Parameters
 */
public interface RuleManagerParams {

    /**
     * Whether or not the rule component is enabled. A value of rule=false disables the rule manager component for the request.
     */
    public static final String RULE = "rule";

    /**
     * Type of page being served (search, category, rule).
     */
    public static final String PAGE_TYPE = "pageType";

    /**
     * List of site IDs to which rules should apply.
     */
    public static final String SITE_IDS = "siteId";

    /**
     * Catalog currently being searched.
     */
    public static final String CATALOG_ID = "catalogId";

    /**
     * Catalog country currently being searched.
     */
    public static final String COUNTRY_ID = "country";

    /**
     * Category filter to apply. Certain rules only apply for specific categories.
     */
    public static final String CATEGORY_FILTER = "categoryFilter";

    /**
     * Whether or not rules debug information should be returned on the response.
     */
    public static final String DEBUG = "debugRule";
    
    /**
     * Indicates if we are in a brand category page
     */
    public static final String BRAND_ID = "brandId";
    
    /**
     * Indicates if we are in a rule category page
     */
    public static final String RULE_PAGE = "rulePage";
}
