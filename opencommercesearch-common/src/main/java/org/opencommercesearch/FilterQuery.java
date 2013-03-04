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

/**
 * This class represents represents a filter query. A filter consist of a field
 * name and a expression.
 * 
 * @author rmerizalde
 * 
 */

public class FilterQuery {
    public static final String SEPARATOR = ":";

    private String fieldName;
    private String expression;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getExpression() {
        return expression;
    }

    public String getUnescapeExpression() {
        return unescapeQueryChars(expression);
    }

    public void setExpression(String filter) {
        this.expression = filter;
    }

    public FilterQuery(String filterQuery) {
        String[] parts = StringUtils.split(filterQuery, SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid filter query " + filterQuery);
        }
        setFieldName(parts[0]);
        setExpression(parts[1]);
    }

    public FilterQuery(String fieldName, String expression) {
        this.setFieldName(fieldName);
        this.setExpression(expression);
    }

    public static FilterQuery[] parseFilterQueries(String filterQueries) {
        String[] array = StringUtils.split(filterQueries, Utils.PATH_SEPARATOR);

        if (array == null) {
            return null;
        }

        FilterQuery[] output = new FilterQuery[array.length];
        
        for (int i = 0; i < array.length; ++i) {
            output[i] = new FilterQuery(array[i]);
        }

        return output;
    }

    public String toString() {
        return getFieldName() + SEPARATOR + getExpression();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        FilterQuery other = (FilterQuery) o;
        return getFieldName().equals(other.getFieldName()) && getExpression().equals(other.getExpression());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + getFieldName().hashCode() + getExpression().hashCode();
    }


    // @TODO move this to org.apache.solr.client.solrj.util.ClientUtils ??
    public static String unescapeQueryChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if ((i + 1) < s.length()) {
                c = s.charAt(i + 1);
                // These characters are part of the query syntax and if escaped
                // lets skip the escape char
                if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^'
                        || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~' || c == '*'
                        || c == '?' || c == '|' || c == '&' || c == ';' || c == '/' || Character.isWhitespace(c)) {
                    continue;
                }
            }
            sb.append('\\');
        }
        return sb.toString();
    }
}
