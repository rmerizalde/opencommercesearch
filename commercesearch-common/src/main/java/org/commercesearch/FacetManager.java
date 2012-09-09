package org.commercesearch;

import java.util.HashMap;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.commercesearch.repository.FacetFieldProperty;
import org.commercesearch.repository.FacetProperty;

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
                String fieldName = (String) facet.getPropertyValue(FacetFieldProperty.FIELD);
                if (manager.getFieldFacet(fieldName) == null) {
                    String localParams = "";
                    Boolean isMultiSelect = (Boolean) facet.getPropertyValue(FacetProperty.IS_MULTI_SELECT);
                    if (isMultiSelect != null && isMultiSelect) {
                        localParams = "{!ex=" + fieldName + "}";
                    }
                    query.addFacetField(localParams + fieldName);
                    setParam(query, fieldName, "limit", (Integer) facet.getPropertyValue(FacetFieldProperty.LIMIT));
                    setParam(query, fieldName, "mincount",
                            (Integer) facet.getPropertyValue(FacetFieldProperty.MIN_COUNT));
                    setParam(query, fieldName, "sort", (String) facet.getPropertyValue(FacetFieldProperty.SORT));
                    setParam(query, fieldName, "missing", (Boolean) facet.getPropertyValue(FacetFieldProperty.MISSING));
                    manager.addFieldFacet(fieldName, facet);
                }
            }

            private void setParam(SolrQuery query, String fieldName, String paramName, Object value) {
                if (value != null) {
                    query.set("f." + fieldName + ".facet." + paramName, value.toString());
                }
            }
        };
        abstract void setParams(FacetManager manager, SolrQuery query, RepositoryItem facet);
    }

    public RepositoryItem getFieldFacet(String fieldName) {
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
}
