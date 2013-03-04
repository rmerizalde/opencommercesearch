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

import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;

public class Utils {
    public static final String PATH_SEPARATOR = "_";
    public static final String RESOURCE_IN_RANGE = "inrange";
    public static final String RESOURCE_BEFORE = "before";
    public static final String RESOURCE_AFTER = "after";

    public static final ResourceBundle resources = ResourceBundle.getBundle("org.opencommercesearch.CSResources");

    public static String createPath(FilterQuery[] filterQueries, FilterQuery skipFilter) {
        if (filterQueries == null) {
            return StringUtils.EMPTY;
        }

        StringBuffer b = new StringBuffer();

        for (FilterQuery filterQuery : filterQueries) {
            if (!filterQuery.equals(skipFilter)) {
                b.append(filterQuery.toString()).append(PATH_SEPARATOR);
            }
        }
        if (b.length() > 0) {
            b.setLength(b.length() - 1);
        }
        return b.toString();
    }

    public static String getRangeName(String fieldName, String key, String value1, String value2) {
        String resource = resources.getString("facet.range." + fieldName + "." + key);

        if (resource == null) {
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
                String key = Utils.RESOURCE_IN_RANGE;
                if ("*".equals(parts[0])) {
                    key = Utils.RESOURCE_BEFORE;
                } else if ("*".equals(parts[1])) {
                    key = Utils.RESOURCE_AFTER;
                }
                return Utils.getRangeName(fieldName, key, parts[0], parts[1]);
            }
        }
        return expression;
    }
    
    public static String findFilterExpressionByName(String path, String filterQueryName){
    	String filterExpression = null;
    	FilterQuery[] filterQueries = FilterQuery.parseFilterQueries(path);
        for(FilterQuery filterQuery :filterQueries) {
            if(filterQueryName.equals(filterQuery.getFieldName())){
            	filterExpression = filterQuery.getExpression();
            }
        }
        
        return filterExpression;
    }
    
}
