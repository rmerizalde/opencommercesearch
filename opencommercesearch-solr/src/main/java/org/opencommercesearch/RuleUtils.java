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

import org.apache.commons.lang.StringUtils;

import java.util.*;

public class RuleUtils {

    private RuleUtils() {}

    public static final String PATH_SEPARATOR = "|";
    public static final String RESOURCE_CRUMB = "crumb";
    public static final String RESOURCE_IN_RANGE = "inrange";
    public static final String RESOURCE_BEFORE = "before";
    public static final String RESOURCE_AFTER = "after";

    public static final ResourceBundle resources = ResourceBundle.getBundle("org.opencommercesearch.CSResources");

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter) {
        return createPath(filterQueries, skipFilter, null);
    }

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter, String replacementFilterQuery) {
        if (filterQueries == null) {
            return StringUtils.EMPTY;
        }

        StringBuilder b = new StringBuilder();

        for (FilterQuery filterQuery : filterQueries) {
            if (!filterQuery.equals(skipFilter)) {
                b.append(filterQuery.toString()).append(PATH_SEPARATOR);
            } else if (replacementFilterQuery != null) {
                b.append(replacementFilterQuery).append(PATH_SEPARATOR);
            }
        }
        if (b.length() > 0) {
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }

    private static String loadResource(String key) {
        try {
            return resources.getString(key);
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    public static String getRangeName(String fieldName, String key, String value1, String value2, String defaultName) {
        String resource;
        String resourceKey = "facet.range." + fieldName + "." + key;

        // First try to find if there's a specific resource for the value
        resource = loadResource(resourceKey + "." + value1);
        if (resource == null) {
            resource = loadResource(resourceKey + "." + value2);
        }

        if (resource == null) {
            resource = loadResource(resourceKey);
        }

        if (resource == null) {
            if (defaultName != null) {
                return defaultName;
            }
            resource = "${v1}-${v2}";
        }

        String rangeName = StringUtils.replace(resource, "${v1}", (value1 == null ? "" : value1));
        rangeName = StringUtils.replace(rangeName, "${v2}", (value2 == null ? "" : value2));
        return rangeName;
    }

    public static String getRangeName(String fieldName, String expression) {
        if (expression.startsWith("[") && expression.endsWith("]")) {
            String[] parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ");
            if (parts.length == 2) {
                String key = RuleUtils.RESOURCE_IN_RANGE;
                if ("*".equals(parts[0])) {
                    key = RuleUtils.RESOURCE_BEFORE;
                } else if ("*".equals(parts[1])) {
                    key = RuleUtils.RESOURCE_AFTER;
                }
                return RuleUtils.getRangeName(fieldName, key, parts[0], parts[1], null);
            }
        }
        return expression;
    }

    public static String getRangeBreadCrumb(String fieldName, String expression) {
        return getRangeBreadCrumb(fieldName, expression, null);
    }

    public static String getRangeBreadCrumb(String fieldName, String expression, String defaultCrumb) {
        if (expression.startsWith("[") && expression.endsWith("]")) {
            String[] parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ");
            if (parts.length == 2) {
                return getRangeName(fieldName, RuleUtils.RESOURCE_CRUMB, parts[0], parts[1], defaultCrumb);
            }
        }
        return expression;
    }
}
