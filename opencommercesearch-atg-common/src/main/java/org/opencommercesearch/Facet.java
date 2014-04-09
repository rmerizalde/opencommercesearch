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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class represents a facet return in the search results. A facet has filter
 * for each possible value. A filter has the count of documents that matches its
 * filter query.
 */
public class Facet {
    public static final Integer DEFAULT_MIN_BUCKETS = 2;

    private String name;
    private List<Filter> filters;
    private Map<String,String> metadata;
    private Integer minBuckets = DEFAULT_MIN_BUCKETS;
    private boolean isMultiSelect;

    /**
     * Indicates whether or not, this facet should display top values (ordered by count) first, and when expanded, show original sorting value (by name).
     */
    private boolean isMixedSorting;

    /**
     *
     * @return the name of the facet
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name for this facet
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return a list of all filters/buckets for this facet
     */
    public List<Filter> getFilters() {
        return filters;
    }

    /**
     *
     * @return a list of the filters/buckets the user has already selected for this facet
     */
    public List<Filter> getSelectedFilters() {
        if (!isMultiSelect()) {
            Collections.emptyList();
        }

        List<Filter> selectedFilter = new ArrayList<Filter>();
        for (Filter filter : filters) {
            if (filter.isSelected()) {
                selectedFilter.add(filter);
            }
        }
        return selectedFilter;
    }

    /**
     *
     * @return a list of the filters/buckets the user can select for this facet
     */
    public List<Filter> getSelectableFilters() {
        if (!isMultiSelect()) {
            return filters;
        }

        List<Filter> selectableFilters = new ArrayList<Filter>();
        for (Filter filter : filters) {
            if (!filter.isSelected()) {
                selectableFilters.add(filter);
            }
        }
        return selectableFilters;
    }

    /**
     * Sets the filters/buckets for this facet
     *
     * @param filters
     */
    public void setFilter(List<Filter> filters) {
        this.filters = filters;
    }
        
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     *
     * @return the minimum number of filter/buckets for this facet in order to show up in the UI.
     */
    public Integer getMinBuckets() {
        return minBuckets;
    }

    /**
     * Sets the minimum number of filters/buckets for this facet in order to show up in the UI.
     * @param minBuckets
     */
    public void setMinBuckets(Integer minBuckets) {
        if (minBuckets != null) {
            this.minBuckets = minBuckets;
        }
    }

    /**
     *
     * @return true if user can select multiple bucket for this facet. Otherwise return false
     */
    public boolean isMultiSelect() {
        return isMultiSelect;
    }

    /**
     * Sets if this facet can have multiple buckets selected or not
     * @param isMultiSelect
     */
    public void setMultiSelect(boolean isMultiSelect) {
        this.isMultiSelect = isMultiSelect;
    }

    /**
     * Indicates whether or not this facet filters should use mixed sorting approach.
     * <p/>
     * Mixed sorting is used for facets that have many filters. Initially facets could display the filters sorted by
     * count, and when expanded (so all values are displayed) sort by index.
     * <p/>
     * Specifics on how this field should work, are left for the front end.
     * @return Whether or not this facet values should use mixed sorting approach.
     */
    public boolean isMixedSorting() {
        return isMixedSorting;
    }

    /**
     * Specify whether or not this facet supports mixed sorting.
     * @param mixedSorting Whether or not this facet supports mixed sorting. See method {@link #isMixedSorting()} for details.
     */
    public void setMixedSorting(boolean mixedSorting) {
        isMixedSorting = mixedSorting;
    }

    @Override
    public String toString() {
        return "Facet [name=" + name + "]";
    }

    /**
     * A filter class represent a bucket in a facet. Each bucket/filter has a name, count of documents
     * and a filter query. In addition, a filter has flag to indicate if the bucket has been selected already.
     * Finally, the filter has a path which include the filter query for this query to select/un-select the bucket
     * plus the filters of all other facets the user has selected already.
     *
     */
    public static class Filter {

        private String name;
        private long count;
        private String path;
        private String filterQuery;
        private boolean isSelected;
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }        
        
        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFilterQuery() {
            return filterQuery;
        }

        public void setFilterQuery(String filterQuery) {
            this.filterQuery = filterQuery;
        }

        public void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
        }

        public boolean isSelected() {
            return isSelected;
        }

        void setSelected(String fieldName, String expression, FilterQuery[] filterQueries) {
            if (filterQueries != null) {
                for (FilterQuery query : filterQueries) {
                    if (query.getFieldName().equals(fieldName) && query.getUnescapeExpression().equals(FilterQuery.unescapeQueryChars(expression))) {
                        setSelected(true);
                        break;
                    }
                }
            }
        }
        
        @Override
        public String toString() {
            return "Filter [name=" + name + ", count=" + count + ", path="
                    + path + ", fq=" + filterQuery + ", selected=" + isSelected + "]";
        }
        
    }
}
