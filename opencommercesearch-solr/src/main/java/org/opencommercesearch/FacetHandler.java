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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.MergedSolrParams;
import org.apache.solr.common.util.NamedList;

import java.util.*;

import static org.opencommercesearch.FacetConstants.*;

/**
 * This class provides functionality to enhance a query with facet params.
 * 
 * @author Javier Mendez
 */
public class FacetHandler {

    /**
     * Set of fields that should be ignored when returning all fields from a facet.
     */
    private static final Set<String> ignoredFields = new HashSet<String>();

    static {
        ignoredFields.add("_version_");
        ignoredFields.add(FacetConstants.FIELD_ID);
        ignoredFields.add(FacetConstants.FIELD_QUERIES);
    }

    /**
     * List of facets registered on this facet handler. The list is indexed by facet field name.
     *
     * @todo jmendez I changed this to a linked hash map to preserver insertion order. However, the rule manager
     * doesn't seem to be loading the facets in the order define by the business user.
     */
    private Map<String, Document> facets = new LinkedHashMap<String, Document>();

    /**
     * Enum of valid facet types known to the facet handler.
     */
    enum FacetType {
        fieldFacet() {
            void setParams(MergedSolrParams query, Document facet) {
                String fieldName = facet.get(FacetConstants.FIELD_FIELD_NAME);
                String localParams = "";
                boolean isMultiSelect = getBooleanFromField(facet.get(FacetConstants.FIELD_MULTISELECT));

                if(isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }

                query.addFacetField(localParams + fieldName);
                setParam(query, fieldName, "limit", facet.get(FacetConstants.FIELD_LIMIT));
                setParam(query, fieldName, "mincount", facet.get(FacetConstants.FIELD_MIN_COUNT));
                setParam(query, fieldName, "sort", facet.get(FacetConstants.FIELD_SORT));

                boolean missing = getBooleanFromField(facet.get(FacetConstants.FIELD_MISSING));
                if(missing) {
                    setParam(query, fieldName, "missing", missing);
                }
            }
        },
        rangeFacet() {
            void setParams(MergedSolrParams query, Document facet) {
                String fieldName = facet.get(FacetConstants.FIELD_FIELD_NAME);
                int start = facet.get(FacetConstants.FIELD_START) != null? Integer.valueOf(facet.get(FacetConstants.FIELD_START)) : 0;
                int end = facet.get(FacetConstants.FIELD_END) != null? Integer.valueOf(facet.get(FacetConstants.FIELD_END)) : 0;
                int gap = facet.get(FacetConstants.FIELD_GAP) != null? Integer.valueOf(facet.get(FacetConstants.FIELD_GAP)) : 0;

                String localParams = "";
                boolean isMultiSelect = getBooleanFromField(facet.get(FacetConstants.FIELD_MULTISELECT));

                if(isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }

                query.addNumericRangeFacet(fieldName, start, end, gap);

                if(StringUtils.isNotBlank(localParams)) {
                    query.add(FacetParams.FACET_RANGE, localParams + fieldName);
                }

                boolean hardened = getBooleanFromField(facet.get(FacetConstants.FIELD_HARDENED));
                if(hardened) {
                    setParam(query, fieldName, "hardened", hardened);
                }

                setParam(query, fieldName, "mincount", 1);
                addRangeParam(query, fieldName, "include", "lower");
                addRangeParam(query, fieldName, "other", "before");
                addRangeParam(query, fieldName, "other", "after");
            }
        },
        dateFacet() {
            void setParams(MergedSolrParams query, Document facet) {
            }
        },
        queryFacet() {
            void setParams(MergedSolrParams query, Document facet) {
                String fieldName = facet.get(FacetConstants.FIELD_FIELD_NAME);
                String localParams = "";
                boolean isMultiSelect = getBooleanFromField(facet.get(FacetConstants.FIELD_MULTISELECT));

                if(isMultiSelect) {
                    localParams = "{!ex=" + fieldName + "}";
                }

                String[] queries = facet.getValues(FacetConstants.FIELD_QUERIES);

                if(queries != null) {
                    for(String q : queries) {
                        q = q.trim();

                        if(!q.startsWith("[") && !q.endsWith("]")) {
                            q = ClientUtils.escapeQueryChars(q);
                        }
                        query.addFacetQuery(localParams + fieldName + ":" + q);
                    }
                }
            }
        };

        abstract void setParams(MergedSolrParams query, Document facet);

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

    /**
     * Utility method that gets a boolean value out of a Solr boolean field value.
     * @param value A Solr boolean field value.
     * @return The corresponding boolean value for the given Solr boolean field value.
     */
    private static boolean getBooleanFromField(String value) {
        return value != null && value.toLowerCase().equals("t");
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
    public void addFacet(Document facet) {
        String fieldName = facet.get(FacetConstants.FIELD_FIELD_NAME);
        addField(fieldName, facet);
    }

    /**
     * Clear all facets in this manager
     */
    public void clear() {
        if (facets != null) {
            facets.clear();
        }
    }

    /**
     * Add facet parameters to the given query.
     * @param query the query object
     */
    public void setParams(MergedSolrParams query) {
        if(facets == null || facets.size() == 0) {
            return;
        }

        for (String s : facets.keySet()) {
            Document facet = facets.get(s);
            FacetType type = FacetType.valueOf(facet.get(FacetConstants.FIELD_TYPE));
            type.setParams(query, facet);
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
    private void addField(String fieldName, Document fieldFacet) {
        facets.put(fieldName, fieldFacet);
    }

    /**
     * Get a facet item by field name.
     * @param fieldName Name of the facet item to get.
     * @return A facet item document if found, null otherwise.
     */
    public Document getFacetItem(String fieldName) {
        return facets.get(fieldName);
    }

    /**
     * Gets facets as an array of named lists.
     * @return All facets registered on this handler, as an array of named lists.
     */
    public NamedList[] getFacets() {
        NamedList[] result = new NamedList[facets.size()];
        int index = 0;
        for(String fieldName : facets.keySet()) {
            NamedList<String> namedList = new NamedList<String>();

            for (IndexableField field : facets.get(fieldName)) {
                if(!ignoredFields.contains(field.name())) {
                    namedList.add(field.name(), field.stringValue());
                }
            }

            result[index++] = namedList;
        }

        return result;
    }
}
