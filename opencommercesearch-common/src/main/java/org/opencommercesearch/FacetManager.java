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

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.FacetParams;
import org.opencommercesearch.repository.FacetProperty;
import org.opencommercesearch.repository.FieldFacetProperty;
import org.opencommercesearch.repository.QueryFacetProperty;
import org.opencommercesearch.repository.RangeFacetProperty;

import atg.repository.RepositoryItem;

/**
 * This class provides functionality to facets defined in the repository to
 * query parameters for the search engine.
 * 
 * The facet manager is part of the search response, which can be sued to
 * retrieve information about facet applied applied to the query.
 * 
 * @author rmerizalde
 *
 * @todo decouple this class from ATG
 * 
 */
public class FacetManager {
    private Map<String, RepositoryItem> facetMap;

    enum FacetType {
        fieldFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(FieldFacetProperty.FIELD);
                String localParams = "";
                Boolean isMultiSelect = (Boolean) facet.getPropertyValue(FacetProperty.IS_MULTI_SELECT);
                if (isMultiSelect != null && isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }
                query.addFacetField(localParams + fieldName);
                setParam(query, fieldName, "limit", (Integer) facet.getPropertyValue(FieldFacetProperty.LIMIT));
                setParam(query, fieldName, "mincount",
                        (Integer) facet.getPropertyValue(FieldFacetProperty.MIN_COUNT));
                setParam(query, fieldName, "sort", (String) facet.getPropertyValue(FieldFacetProperty.SORT));
                setParam(query, fieldName, "missing", (Boolean) facet.getPropertyValue(FieldFacetProperty.MISSING));
            }
        },
        rangeFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(RangeFacetProperty.FIELD);
                Integer start = (Integer) facet.getPropertyValue(RangeFacetProperty.START);
                Integer end = (Integer) facet.getPropertyValue(RangeFacetProperty.END);
                Integer gap = (Integer) facet.getPropertyValue(RangeFacetProperty.GAP);
                String localParams = "";
                Boolean isMultiSelect = (Boolean) facet.getPropertyValue(RangeFacetProperty.IS_MULTI_SELECT);
                if (isMultiSelect != null && isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }

                query.addNumericRangeFacet(fieldName, start, end, gap);
                if (StringUtils.isNotBlank(localParams)) {
                    query.add(FacetParams.FACET_RANGE, localParams + fieldName);
                }
                Boolean hardened = (Boolean) facet.getPropertyValue(RangeFacetProperty.HARDENED);
                if (hardened != null) {
                    setParam(query, fieldName, "hardened", hardened);
                }
                setParam(query, fieldName, "mincount", 1);
                addRangeParam(query, fieldName, "include", "lower");
                addRangeParam(query, fieldName, "other", "before");
                addRangeParam(query, fieldName, "other", "after");
            }
        },
        dateFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
            }
        },
        queryFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(FieldFacetProperty.FIELD);
                String localParams = "";
                Boolean isMultiSelect = (Boolean) facet.getPropertyValue(QueryFacetProperty.IS_MULTI_SELECT);
                if (isMultiSelect != null && isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }
                @SuppressWarnings("unchecked")
                List<String> queries = (List<String>) facet.getPropertyValue(QueryFacetProperty.QUERIES);

                if (queries != null) {
                    for (String q : queries) {
                        q = q.trim();
                        if (!q.startsWith("[") && !q.endsWith("]")) {
                            q = ClientUtils.escapeQueryChars(q);
                        }
                        query.addFacetQuery(localParams + fieldName + ":" + q);
                    }
                }
            }
        };

        abstract void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet);

        void setParam(SolrQuery query, String fieldName, String paramName, Object value) {
            if (value != null) {
                query.set("f." + fieldName + ".facet." + paramName, value.toString());
            }
        }

        void addParam(SolrQuery query, String fieldName, String paramName, Object value) {
            if (value != null) {
                query.add("f." + fieldName + ".facet." + paramName, value.toString());
            }
        }

        void setRangeParam(SolrQuery query, String fieldName, String paramName, Object value) {
            if (value != null) {
                query.set("f." + fieldName + ".facet.range." + paramName, value.toString());
            }
        }

        void addRangeParam(SolrQuery query, String fieldName, String paramName, Object value) {
            if (value != null) {
                query.add("f." + fieldName + ".facet.range." + paramName, value.toString());
            }
        }
    }

    public RepositoryItem getFacetItem(String fieldName) {
        if (facetMap == null) {
            return null;
        }
        return facetMap.get(fieldName);
    }

    /**
     * Returns the names for all facets
     */
    Set<String> facetFieldNames() {
        if (facetMap == null) {
            return Collections.emptySet();
        }
        return facetMap.keySet();
    }

    /**
     * Helper method to process a facet item. The facet is used to populate the
     * facet's parameters to the given query. In addition, the facet is
     * registered for future use. If multiple facet for the same field are added
     * only the first one is used. The rest are ignored
     *
     *            to query to apply the facet params
     * @param facet
     *            the facet item from the repository
     */
    void addFacet(RepositoryItem facet) {
        String fieldName = (String) facet.getPropertyValue(FieldFacetProperty.FIELD);
        addField(fieldName, facet);
    }

    /**
     * Clear all facets in this manager
     */
    void clear() {
        if (facetMap != null) {
            facetMap.clear();
        }
    }

    /**
     * Add facet parameters to the given query.
     * @param query the query object
     */
    void setParams(SolrQuery query) {
        if (facetMap == null || facetMap.size() == 0) {
            return;
        }
        for (RepositoryItem facet : facetMap.values()) {
            FacetType type = FacetType.valueOf((String) facet.getPropertyValue(FacetProperty.TYPE));
            type.setParams(this, query, facet);
        }
    }

    /**
     * This method register the facet for the given fieldName. A linked hash map is used to preserver the insertion order.
     * Facet rules are process sort priority and facet within a rule are already sorted. Using the field name as the key
     * allows redefining a rule of lower priority.
     *
     * @param fieldName the field name of the facet
     * @param fieldFacet the facet object
     */
    private void addField(String fieldName, RepositoryItem fieldFacet) {
        if (facetMap == null) {
            facetMap = new LinkedHashMap<String, RepositoryItem>();
        }
        facetMap.put(fieldName, fieldFacet);
    }

    public String getFacetName(String fieldName) {
        String facetName = fieldName;
        RepositoryItem facetItem = getFacetItem(fieldName);
        if (facetItem != null) {
            facetName = (String) facetItem.getPropertyValue(FacetProperty.NAME);
        }
        return facetName;
    }
    
    public String getFacetName(FacetField facet) {
        String facetName = facet.getName();
        RepositoryItem facetItem = getFacetItem(facet.getName());
        if (facetItem != null) {
            facetName = (String) facetItem.getPropertyValue(FacetProperty.NAME);
        }
        return facetName;
    }

    public String getFacetName(RangeFacet facet) {
        String facetName = facet.getName();
        RepositoryItem facetItem = getFacetItem(facet.getName());
        if (facetItem != null) {
            facetName = (String) facetItem.getPropertyValue(FacetProperty.NAME);
        }
        return facetName;
    }
    
    public Integer getFacetMinBuckets(String fieldName) {
        Integer minBuckets = 2;
        RepositoryItem facetItem = getFacetItem(fieldName);
        if (facetItem != null ) {
            Integer persistedMinBuckets = (Integer) facetItem.getPropertyValue(FacetProperty.MIN_BUCKETS);
            if( persistedMinBuckets != null ) {
                minBuckets = persistedMinBuckets;
            }
        }
        return minBuckets;
    }
    
    public Integer getFacetMinBuckets(FacetField facetField) {
        if (facetField != null) {
            return getFacetMinBuckets(facetField.getName());
        } else {
            return 2;
        }
    }

    public boolean isMultiSelectFacet(String fieldName) {
        RepositoryItem facetItem = getFacetItem(fieldName);
        if (facetItem == null) {
            return false;
        }
        Boolean isMultiSelect = (Boolean) facetItem.getPropertyValue(FacetProperty.IS_MULTI_SELECT);
        return isMultiSelect != null && isMultiSelect;
    }

    public boolean isMultiSelectFacet(FacetField facetField) {
        if (facetField != null) {
            return isMultiSelectFacet(facetField.getName());
        } else {
            return false;
        }
    }
    
    public String getFacetUIType(String fieldName) {
        String uiType = null;
        RepositoryItem facetItem = getFacetItem(fieldName);
        if (facetItem != null) {
            uiType = (String) facetItem.getPropertyValue(FacetProperty.UI_TYPE);
        }
        return uiType;
    }

    public String getFacetUIType(FacetField facetField) {
        if (facetField != null) {
            return getFacetUIType(facetField.getName());
        } else {
            return null;
        }
    }

    public String getCountName(Count count) {
        return getCountName(count, null);
    }

    public String getCountName(Count count, String prefix) {
        String name = count.getName();

        if (prefix != null && name.startsWith(prefix)) {
            name = name.substring(prefix.length());
        }
        return name;
    }

    public String getCountName(RangeFacet.Count count) {
        return count.getValue();
    }

    public String getCountPath(Count count, FilterQuery[] filterQueries) {
        return getCountPath(count.getName(), count.getFacetField().getName(), count.getAsFilterQuery(), filterQueries);
    }

    public String getCountPath(String name, String fieldName, String filterQuery, FilterQuery[] filterQueries) {
        FilterQuery selectedFilterQuery = null;
        if (filterQueries != null) {
            for (FilterQuery query : filterQueries) {
                if (query.getFieldName().equals(fieldName) && query.getUnescapeExpression().equals(name)) {
                    selectedFilterQuery = query;
                } else if (fieldName.equals("category")
                        && query.getFieldName().equals("category")) {
                    selectedFilterQuery = query;
                }
            }
        }


        String path = Utils.createPath(filterQueries, selectedFilterQuery);
        if (selectedFilterQuery != null && !fieldName.equals("category")) {
            return path;
        } else if (StringUtils.isNotBlank(path)) {
            return path + Utils.PATH_SEPARATOR + filterQuery;
        } else {
            return filterQuery;
        }
    }

    public List<BreadCrumb> getBreadCrumbs(FilterQuery[] filterQueries) {
        if (filterQueries == null || filterQueries.length == 0) {
            return Collections.emptyList();
        }
        List<BreadCrumb> crumbs = new ArrayList<BreadCrumb>();

        for (FilterQuery filterQuery : filterQueries) {
            if (filterQuery.getFieldName().equals("category")) {
                createCategoryBreadCrumb(filterQuery, filterQueries, crumbs);
            } else {
                BreadCrumb crumb = new BreadCrumb();
                crumb.setFieldName(filterQuery.getFieldName());
                crumb.setExpression(filterQuery.getUnescapeExpression());
                crumb.setPath(Utils.createPath(filterQueries, filterQuery));
                crumbs.add(crumb);
            }
        }
        return crumbs;
    }

    /**
     * Creates the bread crumbs for the selected categories
     * 
     * @param categoryFilterQuery
     *            the category filter query selected
     * @param breadCrumbs
     *            the output crumb list
     */
    private void createCategoryBreadCrumb(FilterQuery categoryFilterQuery, FilterQuery[] filterQueries,
            List<BreadCrumb> breadCrumbs) {
        if (categoryFilterQuery == null) {
            return;
        }

        String[] categories = StringUtils
                .split(categoryFilterQuery.getExpression(), SearchConstants.CATEGORY_SEPARATOR);

        if (categories.length <= 2) {
            return;
        }

        String catalogId = categories[1];
        StringBuffer buffer = new StringBuffer();
        String basePath = Utils.createPath(filterQueries, categoryFilterQuery);

        int level = 1;
        for (int i = 2; i < categories.length; ++i) {
            BreadCrumb crumb = new BreadCrumb();
            String category = categories[i];

            crumb.setExpression(FilterQuery.unescapeQueryChars(category));

            crumb.setFieldName(categoryFilterQuery.getFieldName());
            String unselectPath = "";
            if (buffer.length() > 0) {
                unselectPath = "category:" + level++ + SearchConstants.CATEGORY_SEPARATOR + catalogId
                        + buffer.toString();
            }

            if (StringUtils.isNotBlank(basePath)) {
                if (unselectPath != null) {
                    unselectPath += Utils.PATH_SEPARATOR;
                }
                unselectPath += basePath;
            }
            crumb.setPath(unselectPath);
            breadCrumbs.add(crumb);
            buffer.append(SearchConstants.CATEGORY_SEPARATOR).append(category);
        }
    }
}
