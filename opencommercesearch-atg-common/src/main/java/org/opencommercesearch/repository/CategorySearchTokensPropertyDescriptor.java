package org.opencommercesearch.repository;

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

import java.util.HashSet;
import java.util.Set;

import org.opencommercesearch.SearchConstants;

import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemImpl;
import atg.repository.RepositoryPropertyDescriptor;

public class CategorySearchTokensPropertyDescriptor extends RepositoryPropertyDescriptor {

    private static final long serialVersionUID = -2727621219614310966L;

    @Override
    public Object getPropertyValue(RepositoryItemImpl item, Object value) {
        Set<String> tokens = new HashSet<String>();
        @SuppressWarnings("unchecked")
        Set<RepositoryItem> parentCategories = (Set<RepositoryItem>) item
                .getPropertyValue(CategoryProperty.FIXED_PARENT_CATEGORIES);

        if (parentCategories == null || parentCategories.size() == 0) {
            @SuppressWarnings("unchecked")
            Set<RepositoryItem> parentCatalogs = (Set<RepositoryItem>) item
                    .getPropertyValue(CategoryProperty.PARENT_CATALOGS);
            if (parentCatalogs != null) {
                for (RepositoryItem catalog : parentCatalogs) {
                    tokens.add("0" + SearchConstants.CATEGORY_SEPARATOR + catalog.getRepositoryId());
                }
            }
        } else {
            for (RepositoryItem category : parentCategories) {
                @SuppressWarnings("unchecked")
                Set<String> searchTokens = (Set<String>) category.getPropertyValue(CategoryProperty.SEARCH_TOKENS);

                for (String searchToken : searchTokens) {
                    int index = searchToken.indexOf(SearchConstants.CATEGORY_SEPARATOR);
                    if (index == -1) {
                        continue;
                    }
                    int depth = Integer.parseInt(searchToken.substring(0, index)) + 1;
                    tokens.add(depth + searchToken.substring(index) + SearchConstants.CATEGORY_SEPARATOR
                            + item.getItemDisplayName());
                }
            }
        }

        return tokens;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isQueryable() {
        return false;
    }
}
