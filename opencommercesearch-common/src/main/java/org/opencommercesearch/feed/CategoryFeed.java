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

import java.util.Collection;
import java.util.Set;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.opencommercesearch.Utils;
import org.opencommercesearch.api.ProductService;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;

/**
 * A feed for categories.
 */
public class CategoryFeed extends BaseRestFeed {

    public static String[] REQUIRED_FIELDS = { "id", "name" };

    /**
     * Return the Endpoint for this feed
     * @return an Endpoint enum representing the endpoint for this feed
     */
    public ProductService.Endpoint getEndpoint() {
        return ProductService.Endpoint.CATEGORIES;
    }

    /**
     * Convert the given repository item to its corresponding JSON API format.
     * @param item Repository item to convert.
     * @return The JSON representation of the given repository item, or null if there are missing fields.
     * @throws atg.json.JSONException if there are format issues when creating the JSON object.
     * @throws atg.repository.RepositoryException if item data from the list can't be read.
     */
    protected JSONObject repositoryItemToJson(RepositoryItem item) throws JSONException, RepositoryException {
        JSONObject category = new JSONObject();

        category.put("id", item.getRepositoryId());
        category.put("name", item.getItemDisplayName());
        category.put("isRuleBased", RuleBasedCategoryProperty.ITEM_DESCRIPTOR.equals(item.getItemDescriptor().getItemDescriptorName()));
        setIdsProperty(category, "catalogs", (Collection<RepositoryItem>) item.getPropertyValue("catalogs"), false);
        setIdsProperty(category, "parentCategories", (Collection<RepositoryItem>) item.getPropertyValue("fixedParentCategories"), true);
        setIdsProperty(category, "childCategories", (Collection<RepositoryItem>) item.getPropertyValue("fixedChildCategories"), true);

        category.put("filter", getCategoryFilter(item));
        setCategoryPaths(category, item);

        return category;
    }

    private void setIdsProperty(JSONObject obj, String propertyName, Collection<RepositoryItem> items, boolean asObject) throws JSONException {
        if (items != null) {
            JSONArray jsonItems = new JSONArray();
            for (RepositoryItem item : items) {
                if (asObject) {
                    JSONObject itemObj = new JSONObject();
                    itemObj.put("id", item.getRepositoryId());
                    jsonItems.add(itemObj);
                } else {
                    jsonItems.add(item.getRepositoryId());
                }
            }
            obj.put(propertyName, jsonItems);
        }
    }

    /**
     * Gets the filter for this category. Example: Men's Jackets -> 3.bcs.Men's Clothing.Men's Jackets
     * <p/>
     * This field is used by search engines to filter out results based on the current category, among other possible applications.
     * <p/>
     * If this is a rule category, the path should be null.
     * @param categoryItem The category repository item
     * @return Category filter for the given category.
     * @throws RepositoryException If there are issues retrieving information from the given category.
     */
    private String getCategoryFilter(RepositoryItem categoryItem) throws RepositoryException {
        String filter = null;

        if(!RuleBasedCategoryProperty.ITEM_DESCRIPTOR.equals(categoryItem.getItemDescriptor().getItemDescriptorName())) {
            Set<String> searchTokens = (Set<String>) categoryItem.getPropertyValue(CategoryProperty.SEARCH_TOKENS);
            //Search tokens is a calculated field on categories, and not all categories have this field.
            if (searchTokens != null && searchTokens.size() > 0) {
                filter =  ClientUtils.escapeQueryChars(searchTokens.iterator().next());
            }
        }

        return filter;
    }

    /**
     * Adds the category paths for each catalog.
     * <p/>
     * These paths are used by search engines to filter out results based on the current category, among other possible applications.
     * @param obj JSON object to add the category paths.
     * @param category The category item used to calculate paths.
     * @throws JSONException If there are JSON generation issues.
     */
    private void setCategoryPaths(JSONObject obj, RepositoryItem category) throws JSONException {
        Collection<RepositoryItem> catalogs = (Collection<RepositoryItem>) category.getPropertyValue("catalogs");

        if(catalogs == null) {
            return;
        }

        JSONArray paths = new JSONArray();
        for(RepositoryItem catalog : catalogs) {
            paths.add(Utils.buildCategoryPrefix(catalog.getRepositoryId(), category));
        }

        obj.put("paths", paths);
    }

    /**
     * Return a list of required fields when transforming a repository item to JSON.
     * <p/>
     * This list is used for logging purposes only.
     * @return List of required fields when transforming a repository item to JSON, required for logging purposes.
     */
    protected String[] getRequiredItemFields() {
        return REQUIRED_FIELDS;
    }
}
