package org.opencommercesearch.feed;

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

import atg.json.JSONArray;
import atg.json.JSONException;
import atg.json.JSONObject;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import org.opencommercesearch.FacetConstants;
import org.opencommercesearch.api.ProductService;
import org.opencommercesearch.repository.FacetProperty;

import java.util.*;

/**
 * This class represents a feed from ATG to the OpenCommerceSearch REST API.
 * <p/>
 * The feed will fetch any matching facets from the database and send them over HTTP to the facets API endpoint, which should index
 * them to Solr.
 *
 * @author Javier Mendez
 */
public class FacetFeed extends BaseRestFeed {

    /**
     * Return the Endpoint for this feed
     * @return an Endpoint enum representing the endpoint for this feed
     */
    public ProductService.Endpoint getEndpoint() {
        return ProductService.Endpoint.FACETS;
    }

    @Override
    protected JSONObject repositoryItemToJson(RepositoryItem facet) throws JSONException, RepositoryException {
        //Convert facet to JSON
        JSONObject facetJsonObj = new JSONObject();
        String facetType = (String) facet.getPropertyValue(FacetProperty.TYPE);

        facetJsonObj.put(FacetConstants.FIELD_ID, facet.getRepositoryId());
        facetJsonObj.put(FacetConstants.FIELD_NAME, facet.getItemDisplayName());
        facetJsonObj.put(FacetConstants.FIELD_FIELD_NAME, facet.getPropertyValue(FacetProperty.FIELD));
        facetJsonObj.put(FacetConstants.FIELD_TYPE, facetType);
        facetJsonObj.put(FacetConstants.FIELD_UI_TYPE, facet.getPropertyValue(FacetProperty.UI_TYPE));
        facetJsonObj.put(FacetConstants.FIELD_MULTISELECT, facet.getPropertyValue(FacetProperty.IS_MULTI_SELECT));
        facetJsonObj.put(FacetConstants.FIELD_MIN_BUCKETS, facet.getPropertyValue(FacetProperty.MIN_BUCKETS));

        if(facetType.equals(FacetConstants.FACET_TYPE_FIELD)) {
            facetJsonObj.put(FacetConstants.FIELD_MIXED_SORTING, facet.getPropertyValue(FacetProperty.IS_MIXED_SORTING));
            facetJsonObj.put(FacetConstants.FIELD_MIN_COUNT, facet.getPropertyValue(FacetProperty.MIN_COUNT));
            facetJsonObj.put(FacetConstants.FIELD_SORT, facet.getPropertyValue(FacetProperty.SORT));
            facetJsonObj.put(FacetConstants.FIELD_MISSING, facet.getPropertyValue(FacetProperty.MISSING));
            facetJsonObj.put(FacetConstants.FIELD_LIMIT, facet.getPropertyValue(FacetProperty.LIMIT));
        }

        if(facetType.equals(FacetConstants.FACET_TYPE_DATE) || facetType.equals(FacetConstants.FACET_TYPE_RANGE)) {
            facetJsonObj.put(FacetConstants.FIELD_START, facet.getPropertyValue(FacetProperty.START).toString());
            facetJsonObj.put(FacetConstants.FIELD_END, facet.getPropertyValue(FacetProperty.END).toString());
            facetJsonObj.put(FacetConstants.FIELD_GAP, facet.getPropertyValue(FacetProperty.GAP).toString());
            facetJsonObj.put(FacetConstants.FIELD_HARDENED, facet.getPropertyValue(FacetProperty.HARDENED));
        }

        if(facetType.equals(FacetConstants.FACET_TYPE_QUERY)) {
            @SuppressWarnings("unchecked")
            List<String> queries = (List<String>) facet.getPropertyValue(FacetProperty.QUERIES);
            if (queries != null && queries.size() > 0) {
                List catalogIds = new JSONArray();
                for (String query : queries) {
                    catalogIds.add(query);
                }

                facetJsonObj.put(FacetConstants.FIELD_QUERIES, catalogIds);
            } else {
                facetJsonObj.put(FacetConstants.FIELD_QUERIES, new JSONArray());
            }
        }

        return facetJsonObj;
    }

    @Override
    protected String[] getRequiredItemFields() {
        return null;
    }
}
