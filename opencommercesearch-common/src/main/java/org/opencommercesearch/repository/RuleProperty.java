package org.opencommercesearch.repository;

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
 * Constants for the property names in the rule item descriptor
 * 
 * @author rmerizalde
 */
public class RuleProperty {
    protected RuleProperty() {
    }

    public static final String ID = "id";
    public static final String QUERY = "query";
    public static final String TARGET = "target";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String SITES = "sites";
    public static final String CATALOGS = "catalogs";
    public static final String CATEGORIES = "categories";
    public static final String INCLUDE_SUBCATEGORIES = "includeSubcategories";
    public static final String RULE_TYPE = "ruleType";
    public static final String SORT_PRIORITY = "sortPriority";
    public static final String COMBINE_MODE = "combineMode";

    public static final String TYPE_BOOST_RULE = "boostRule";
    public static final String TYPE_BLOCK_RULE = "blockRule";
    public static final String TYPE_FACET_RULE = "facetRule";
    public static final String TYPE_RANKING_RULE = "rankingRule";
    public static final String TYPE_REDIRECT_RULE  = "redirectRule";

    public static final String COMBINE_MODE_REPLACE = "Replace";
    public static final String COMBINE_MODE_APPEND = "Append";
}
