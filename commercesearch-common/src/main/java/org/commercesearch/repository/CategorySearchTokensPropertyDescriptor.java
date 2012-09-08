package org.commercesearch.repository;

import java.util.HashSet;
import java.util.Set;

import org.commercesearch.SearchConstants;

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
