package org.commercesearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.common.params.FacetParams;
import org.commercesearch.repository.FacetProperty;
import org.commercesearch.repository.FieldFacetProperty;
import org.commercesearch.repository.QueryFacetProperty;
import org.commercesearch.repository.RangeFacetProperty;

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
 */
public class FacetManager {
    private Map<String, RepositoryItem> fieldFacets;

    enum FacetType {
        fieldFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(FieldFacetProperty.FIELD);
                if (manager.getFacetItem(fieldName) == null) {
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
                    manager.addFieldFacet(fieldName, facet);
                }
            }
        },
        rangeFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(RangeFacetProperty.FIELD);
                if (manager.getFacetItem(fieldName) == null) {
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


                    manager.addFieldFacet(fieldName, facet);
                }
            }
        },
        dateFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
            }
        },
        queryFacet() {
            void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet) {
                String fieldName = (String) facet.getPropertyValue(FieldFacetProperty.FIELD);
                if (manager.getFacetItem(fieldName) == null) {
                    String localParams = "";
                    Boolean isMultiSelect = (Boolean) facet.getPropertyValue(QueryFacetProperty.IS_MULTI_SELECT);
                    if (isMultiSelect != null && isMultiSelect) {
                        localParams = "{!ex=" + fieldName + "}";
                    }
                    @SuppressWarnings("unchecked")
                    List<String> queries = (List<String>) facet.getPropertyValue(QueryFacetProperty.QUERIES);

                    if (queries != null) {
                        for (String q : queries) {
                            query.addFacetQuery(localParams + fieldName + ":" + q);
                        }
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
        if (fieldFacets == null) {
            return null;
        }
        return fieldFacets.get(fieldName);
    }

    /**
     * Helper method to process a facet item. The facet is used to populate the
     * facet's parameters to the given query. In addition, the facet is
     * registered for future use. If multiple facet for the same field are added
     * only the first one is used. The rest are ignored
     * 
     * @param query
     *            to query to apply the facet params
     * @param facet
     *            the facet item from the repository
     */
    void addFacet(SolrQuery query, RepositoryItem facet) {
        FacetType type = FacetType.valueOf((String) facet.getPropertyValue(FacetProperty.TYPE));

        type.setParams(this, query, facet);
    }

    // Helper method to to register a field facet to this manager
    private void addFieldFacet(String fieldName, RepositoryItem fieldFacet) {
        if (fieldFacets == null) {
            fieldFacets = new HashMap<String, RepositoryItem>();
        }
        fieldFacets.put(fieldName, fieldFacet);
    }

    public String getFacetName(String fieldName) {
        String facetName = fieldName;
        RepositoryItem facetItem = getFacetItem(fieldName);
        if (facetItem != null) {
            facetName = facetItem.getItemDisplayName();
        }
        return facetName;
    }

    public String getFacetName(FacetField facet) {
        String facetName = facet.getName();
        RepositoryItem facetItem = getFacetItem(facet.getName());
        if (facetItem != null) {
            facetName = facetItem.getItemDisplayName();
        }
        return facetName;
    }

    public String getFacetName(RangeFacet facet) {
        String facetName = facet.getName();
        RepositoryItem facetItem = getFacetItem(facet.getName());
        if (facetItem != null) {
            facetName = facetItem.getItemDisplayName();
        }
        return facetName;
    }

    public String getCountName(Count count) {
        String name = count.getName();
        int lastIndex = name.lastIndexOf(SearchConstants.CATEGORY_SEPARATOR);
        if (lastIndex != -1) {
            name = name.substring(lastIndex + 1);
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
     * @param crumbs
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
